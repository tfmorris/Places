/*
 * Copyright 2012 Foundation for On-Line Genealogy, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.folg.places.standardize;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.MemcachedClient;

import javax.sql.DataSource;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * User: dallan
 * Date: 1/10/12
 */
public class Standardizer {
   /**
    * Standardization mode:
    * BEST=get the closest country
    * REQUIRED=match must include the left-most level or not at all,
    * NEW=BEST+1 -- if you can't include the next level to the left, return a fake place with it as the name
    */
   public static enum Mode { BEST, REQUIRED, NEW };

   public static int MAX_LEVELS = 4;

   private static Logger logger = Logger.getLogger("org.folg.places.standardize");
   private static int USA_ID = 1500;
   private static Standardizer standardizer = new Standardizer();

   public static Standardizer getInstance() {
      return standardizer;
   }

   public static class PlaceScore {
      private Place place;
      private double score;

      public PlaceScore(Place place, double score) {
         this.place = place;
         this.score = score;
      }

      public Place getPlace() {
         return place;
      }

      public double getScore() {
         return score;
      }
   }

   private static ComboPooledDataSource staticDS = null;
   private static synchronized DataSource getDataSource(String driverClass, String jdbcUrl, String user, String password) {
     if (staticDS == null) {
        staticDS = new ComboPooledDataSource();
        try {
           Class.forName(driverClass).newInstance();
           staticDS.setDriverClass(driverClass);
        } catch (Exception e) {
           throw new RuntimeException("Error loading database driver: "+e.getMessage());
        }
        staticDS.setJdbcUrl(jdbcUrl);
        staticDS.setUser(user);
        staticDS.setPassword(password);
        Runtime.getRuntime().addShutdownHook(new Thread() {
           public void run() {
              try {
                 DataSources.destroy(staticDS);
              } catch (SQLException e) {
                 // ignore
              }
           }
        });
     }
     return staticDS;
   }

   private static class DaemonBinaryConnectionFactory extends BinaryConnectionFactory {
      @Override
      public boolean isDaemon() {
         return true;
      }
   }

   private static MemcachedClient staticMC = null;
   private static synchronized MemcachedClient getMemcachedClient(String memcacheAddresses) {
      // assume memcacheAddresses parameter always has the same value
      if (staticMC == null) {
         try {
            staticMC = new MemcachedClient(new DaemonBinaryConnectionFactory(),
                                           AddrUtil.getAddresses(memcacheAddresses));
         } catch (IOException e) {
            logger.severe("Unable to initialize memcache client");
         }
      }
      return staticMC;
   }

   private Normalizer normalizer = null;
   private Set<String> typeWords = null;
   private Map<String,String> abbreviations = null;
   private Set<String> noiseWords = null;
   private Map<Integer,Place> placeIndex = null;
   private Map<String,Integer[]> wordIndex = null;
   private DataSource dataSource = null;
   private MemcachedClient memcachedClient = null;
   private String memcacheKeyPrefix = null;
   private int memcacheExpiration = 0;
   private Set<Integer> largeCountries = null;
   private Set<Integer> mediumCountries = null;
   private double primaryMatchWeight = 0;
   private Double[] largeCountryLevelWeights = null;
   private Double[] mediumCountryLevelWeights = null;
   private Double[] smallCountryLevelWeights = null;
   private ErrorHandler errorHandler = null;

   private Standardizer() {
      normalizer = Normalizer.getInstance();

      Reader indexReader = null;
      Reader matchCountsReader = null;

      try {
         // read properties
         Properties props = new Properties();
         props.load(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("standardizer.properties"), "UTF8"));

         // read type words
         typeWords = new HashSet<String>(Arrays.asList(props.getProperty("typeWords").split(",")));

         // read abbreviations
         abbreviations = new HashMap<String, String>();
         for (String abbrMap : props.getProperty("abbreviations").split(",")) {
            String[] fields = abbrMap.split("=");
            abbreviations.put(fields[0],fields[1]);
         }

         // read noise words
         noiseWords = new HashSet<String>(Arrays.asList(props.getProperty("noiseWords").split(",")));

         // read large countries
         largeCountries = toIntegerSet(props.getProperty("largeCountries"));

         // read medium countries
         mediumCountries = toIntegerSet(props.getProperty("mediumCountries"));

         // read large country level weights
         largeCountryLevelWeights = toDoubleArray(props.getProperty("largeCountryLevelWeights"));

         // read large country level weights
         mediumCountryLevelWeights = toDoubleArray(props.getProperty("mediumCountryLevelWeights"));

         // read large country level weights
         smallCountryLevelWeights = toDoubleArray(props.getProperty("smallCountryLevelWeights"));

         primaryMatchWeight = Double.parseDouble(props.getProperty("primaryMatchWeight"));

         // initialize db+memcache
         InputStream propStream = getClass().getClassLoader().getResourceAsStream("db_memcache.properties");
         if (propStream != null) {
            props = new Properties();
            props.load(new InputStreamReader(propStream, "UTF8"));
            // read common similar names, either from the database or from a file
            String databaseDriver = props.getProperty("databaseDriver");
            if (databaseDriver != null) {
               // given and surname Standardizer's share the same dataSource
               dataSource = getDataSource(databaseDriver,
                                         props.getProperty("databaseURL"),
                                         props.getProperty("databaseUser"),
                                         props.getProperty("databasePassword"));

               // given and surname Standardizer's share the same memcachedClient
               String memcacheAddresses = props.getProperty("memcacheAddresses");
               if (memcacheAddresses != null) {
                  memcachedClient = getMemcachedClient(memcacheAddresses);
                  memcacheKeyPrefix = props.getProperty("memcacheKeyPrefix")+"p|";
                  memcacheExpiration = Integer.parseInt(props.getProperty("memcacheExpiration"));
               }
            }
         }

         // if not reading from database, read from file
         if (dataSource == null) {
            indexReader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("place_words.csv"), "UTF8");
            readWordIndex(indexReader);
            indexReader.close();

            indexReader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("places.csv"), "UTF8");
            readPlaceIndex(indexReader);
         }
      }
      catch (IOException e) {
         throw new RuntimeException("Error reading file:" + e.getMessage());
      } finally {
         try {
            if (matchCountsReader != null) {
               matchCountsReader.close();
            }
            if (indexReader != null) {
               indexReader.close();
            }
         }
         catch (IOException e) {
            // ignore
         }
      }
   }

   private Set<Integer> toIntegerSet(String value) {
      Set<Integer> result = new HashSet<Integer>();
      for (String field : value.split(",")) {
         result.add(Integer.parseInt(field));
      }
      return result;
   }

   private Double[] toDoubleArray(String value) {
      String[] fields = value.split(",");
      Double[] result = new Double[fields.length];
      for (int i = 0; i < fields.length; i++) {
         result[i] = Double.parseDouble(fields[i]);
      }
      return result;
   }

   /**
    * Read the word index
    * You would not normally call this function. Used in testing
    */
   public void readWordIndex(Reader reader) throws IOException {
      wordIndex = new HashMap<String, Integer[]>();
      BufferedReader r = new BufferedReader(reader);
      String line;
      while ((line = r.readLine()) != null) {
         String[] fields = line.split("\\|");
         String[] idStrings = fields[1].split(",");
         Integer[] ids = new Integer[idStrings.length];
         for (int i = 0; i < idStrings.length; i++) {
            ids[i] = Integer.parseInt(idStrings[i]);
         }

         wordIndex.put(fields[0], ids);
      }
   }

   /**
    * Read the place index
    * You would not normally call this function. Used in testing
    */
   public void readPlaceIndex(Reader reader) throws IOException {
      placeIndex = new HashMap<Integer, Place>();
      BufferedReader r = new BufferedReader(reader);
      String line;
      while ((line = r.readLine()) != null) {
         String[] fields = line.split("\\|");
         Place p = new Place();
         p.setStandardizer(this);
         p.setId(Integer.parseInt(fields[0]));
         p.setName(fields[1]);
         if (fields[2].length() > 0) p.setAltNames(fields[2].split(","));
         if (fields[3].length() > 0) p.setTypes(fields[3].split(","));
         p.setLocatedInId(Integer.parseInt(fields[4]));
         if (fields[5].length() > 0) {
            String[] idStrings = fields[5].split(",");
            int[] ids = new int[idStrings.length];
            for (int i = 0; i < idStrings.length; i++) {
               ids[i] = Integer.parseInt(idStrings[i]);
            }
            p.setAlsoLocatedInIds(ids);
         }
         p.setLevel(Integer.parseInt(fields[6]));
         p.setCountry(Integer.parseInt(fields[7]));
         if (fields.length > 8 && fields[8].length() > 0) p.setLatitude(Double.parseDouble(fields[8]));
         if (fields.length > 9 && fields[9].length() > 0) p.setLongitude(Double.parseDouble(fields[9]));

         placeIndex.put(p.getId(), p);
      }
   }

   public void setErrorHandler(ErrorHandler errorHandler) {
      this.errorHandler = errorHandler;
   }

   // return null if word not found
   private List<Integer> lookupWord(String word) {
      Integer[] ids = wordIndex.get(word);
      if (ids != null) {
         return Arrays.asList(ids);
      }
      return null;
   }

   public Place getPlace(int id) {
      Place p = placeIndex.get(id);
      if (p == null) {
         logger.severe("Place not found: "+id);
      }
      return p;
   }

   public String generatePlaceName(List<String> words) {
      int len = words.size()-1;

      // ignore type words at the end
      // keep Cemetery as part of the full name (it's an exception; if there are others I'll create a property list)
      while (len >= 0 && isTypeWord(words.get(len)) && !"cemetery".equals(words.get(len))) {
         len--;
      }
      // if all words are type words, keep them all
      if (len < 0) {
         len = words.size()-1;
      }

      // join and capitalize
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i <= len; i++) {
         if (buf.length() > 0) {
            buf.append(" ");
         }
         String word = words.get(i);
         buf.append(word.substring(0,1).toUpperCase()+(word.length() > 1 ? word.substring(1).toLowerCase() : ""));
      }
      return buf.toString();
   }

   private boolean checkAncestorMatch(int id, List<Integer> ids) {
      Place p = getPlace(id);
      int locatedInId = p.getLocatedInId();
      if (locatedInId > 0) {
         if (ids.contains(locatedInId) || checkAncestorMatch(locatedInId, ids)) {
            return true;
         }
      }
      if (p.getAlsoLocatedInIds() != null) {
         for (int alii : p.getAlsoLocatedInIds()) {
            if (ids.contains(alii) || checkAncestorMatch(alii, ids)) {
               return true;
            }
         }
      }
      return false;
   }

   private List<Integer> filterSubplaceMatches(List<Integer> children, List<Integer> parents) {
      List<Integer> result = new ArrayList<Integer>();

      for (int child : children) {
         if (checkAncestorMatch(child, parents)) {
            result.add(child);
         }
      }

      return result;
   }

   private List<Integer> filterTypeMatches(String typeToken, List<Integer> ids) {
      List<Integer> result = new ArrayList<Integer>();

      for (int id : ids) {
         Place p = getPlace(id);
         String normalizedName = normalizer.normalize(p.getName());
         // does primary name contain the type words?
         if (normalizedName.indexOf(typeToken) >= 0) {
            result.add(id);
         }
         else if (p.getTypes() != null) {
            for (String type : p.getTypes()) {
               String normalizedType = normalizer.normalize(type);
               // does one of the types contain the type words?
               if (normalizedType.indexOf(typeToken) >= 0) {
                  result.add(id);
                  break;
               }
            }
         }
      }

      return result;
   }

   private double scoreMatch(String nameToken, Place p) {
      String normalizedName = normalizer.normalize(p.getName());
      boolean isPrimaryNameMatch = normalizedName.indexOf(nameToken) >= 0;
      int level = p.getLevel();
      int countryId = p.getCountry();
      Double[] weights;

      if (largeCountries.contains(countryId)) {
         weights = largeCountryLevelWeights;
      }
      else if (mediumCountries.contains(countryId)) {
         weights = mediumCountryLevelWeights;
      }
      else {
         weights = smallCountryLevelWeights;
      }

      double score = weights[Math.min(MAX_LEVELS,level)-1];

      if (isPrimaryNameMatch) {
         score += primaryMatchWeight;
      }

      return score;
   }

   public boolean isTypeWord(String word) {
      String expansion = abbreviations.get(word);
      if (expansion != null) {
         word = expansion;
      }
      return typeWords.contains(word);
   }

   // catenate all of the words together into one token, with ending type words in a second token
   private String[] getNameTypeToken(List<String> words, int wordsToSkip) {
      StringBuilder buf = new StringBuilder();
      String[] result = new String[2];
      result[0] = null; // name token
      result[1] = null; // type token (optional)
      boolean foundNameWord = false;
      for (int i = words.size()-1; i >= wordsToSkip; i--) {
         String word = words.get(i);
         if (word.length() > 0) {
            // skip everything before or or now
            if (i > wordsToSkip && buf.length() > 0 && "or".equals(word) || "now".equals(word)) {
               break;
            }
            // expand abbreviations only if there is >1 word in the phrase
            // keeps from expanding places like No, Niigata, Japan into North
            if (words.size() - wordsToSkip > 1) {
               String expansion = abbreviations.get(word);
               if (expansion != null) {
                  word = expansion;
               }
            }
            if (!typeWords.contains(word)) {
               // type words after a name word go into the type token position
               if (!foundNameWord && buf.length() > 0) {
                  result[1] = buf.toString();
                  buf.setLength(0);
               }
               foundNameWord= true;
            }
            buf.insert(0,word);
         }
      }
      if (buf.length() > 0) {
         result[0] = buf.toString();
      }
      return result;
   }

   private boolean containsNonNoiseWords(List<String> words) {
      for (String word : words) {
         if (!noiseWords.contains(word)) {
            return true;
         }
      }
      return false;
   }

   private boolean containsNonNoiseLevels(List<List<String>> levelWords) {
      for (List<String> words : levelWords) {
         if (containsNonNoiseWords(words)) {
            return true;
         }
      }
      return false;
   }

   // once you've matched a country or a US state, you can't skip over it
   private boolean isSkippable(List<Integer> ids) {
      for (int id : ids) {
         Place p = getPlace(id);
         if (p.getLevel() == 1 ||
             (p.getLevel() == 2 && p.getCountry() == USA_ID)) {
            return false;
         }
      }
      return true;
   }

   private List<Integer> removeChildIds(List<Integer> currentIds) {
      if (currentIds != null) {
         List<Integer> ids = new ArrayList<Integer>();
         for (int id : currentIds) {
            if (!checkAncestorMatch(id, currentIds)) {
               ids.add(id);
            }
         }
         currentIds = ids;
      }
      return currentIds;
   }

   public List<PlaceScore> standardize(String text, String defaultCountry, Mode mode, int numResults) {
      List<List<String>> levelWords = normalizer.tokenize(text);
      List<Integer> currentIds = null;
      List<Integer> previousIds = null;
      String currentNameToken = null;
      int lastFoundLevel = -1;
      // log only the first error per place -- skipping words can result in multiple errors, but we want to log the whole phrase
      boolean errorLogged = false;

      for (int level = levelWords.size()-1; level >= 0; level--) {
         List<String> words = levelWords.get(level);
         // if all words don't match, back off and insert left-hand words as a new level
         // (for people who don't use commas)
         int wordsToSkip = 0;
         List<Integer> ids = null;
         String[] nameType = null;
         while (wordsToSkip < words.size()) {
            nameType = getNameTypeToken(words, wordsToSkip);

            // lookup name token
            ids = lookupWord(nameType[0]);
            if (ids != null) {
               break;
            }
            wordsToSkip++;
         }
         if (ids != null && wordsToSkip > 0) {
            List<String> newLevel = new ArrayList<String>();
            for (int i = 0; i < wordsToSkip; i++) {
               String word = words.get(i);
               // don't push noise words or type words down to the lower level
               // (does it hurt not to push type words down?)
               if (!noiseWords.contains(word) && !isTypeWord(word)) {
                  newLevel.add(word);
               }
            }
            if (newLevel.size() > 0) {
               levelWords.add(level, newLevel);
               level++;
            }
         }

         // didn't find any matches; log and ignore
         if (ids == null) {
            if (errorHandler != null && !errorLogged && containsNonNoiseWords(words)) {
               errorHandler.tokenNotFound(text, levelWords, level, removeChildIds(currentIds));
               errorLogged = true;
            }
         }
         else {
            // if we found previous matches, filter subplaces
            boolean ignoreTypeToken = false;
            if (currentIds != null) {
               List<Integer> matchingIds = filterSubplaceMatches(ids, currentIds);
               // didn't find any children, try skipping over the previous level
               if (matchingIds.size() == 0 && isSkippable(currentIds)) {
                  // try attaching to the grandparent level if there is one
                  if (previousIds != null && previousIds.size() > 0) {
                     matchingIds = filterSubplaceMatches(ids, previousIds);
                     if (matchingIds.size() > 0) {
                        currentIds = previousIds;
                        if (errorHandler != null && !errorLogged) {
                           errorHandler.skippingParentLevel(text, levelWords, level, removeChildIds(matchingIds));
                           errorLogged = true;
                        }
                     }
                  }
                  // else if there is no grandparent level and we matched non-skippable places, go with what we just found
                  else if (!isSkippable(ids)) {
                     matchingIds = ids;
                     currentIds = null;
                     if (errorHandler != null && !errorLogged) {
                        errorHandler.skippingParentLevel(text, levelWords, level, removeChildIds(matchingIds));
                        errorLogged = true;
                     }
                  }
               }

               // still didn't find any children; log and ignore
               if (matchingIds.size() == 0) {
                  ignoreTypeToken = true; // no sense matching the type if we couldn't match the name
                  if (errorHandler != null && !errorLogged && containsNonNoiseWords(words)) {
                     errorHandler.tokenNotFound(text, levelWords, level, removeChildIds(currentIds));
                     errorLogged = true;
                  }
                  ids = currentIds;
                  currentIds = previousIds;
               }
               else {
                  lastFoundLevel = level;
                  ids = matchingIds;
               }
            }
            else {
               lastFoundLevel = level;
            }

            // if we still have multiple matches, filter on type
            if (ids.size() > 1 && nameType[1] != null && !ignoreTypeToken) {
               List<Integer> matchingIds = filterTypeMatches(nameType[1], ids);
               // didn't find a type match; log and ignore
               if (matchingIds.size() == 0) {
                  if (errorHandler != null && !errorLogged) {
                     errorHandler.typeNotFound(text, levelWords, level, removeChildIds(ids));
                     errorLogged = true;
                  }
               }
               else {
                  ids = matchingIds;
               }
            }

            previousIds = currentIds;
            currentIds = ids;
            currentNameToken = nameType[0];
         }
      }

      List<PlaceScore> results = new ArrayList<PlaceScore>();
      // if we have no matches, return empty
      if (currentIds == null) {
         // log this even if we've logged another error earlier
         if (errorHandler != null && containsNonNoiseLevels(levelWords)) {
            errorHandler.placeNotFound(text, levelWords);
         }
      }
      else if (mode == mode.REQUIRED && lastFoundLevel != 0) {
         // don't return any results if we didn't match the last level in this mode
      }
      else {
         // if we have multiple matches and a default country, filter subplaces of the default country
         if (currentIds.size() > 1 && defaultCountry != null && defaultCountry.length() > 0) {
            // TODO - handle default country

         }

         // remove children if we have the parents
         if (currentIds.size() > 1) {
            currentIds = removeChildIds(currentIds);
         }

         // if we have still have multiple matches, score them and return the highest-scoring
         if (currentIds.size() > 1) {
            for (int id : currentIds) {
               Place p = getPlace(id);
               results.add(new PlaceScore(p, scoreMatch(currentNameToken, p)));
            }
            Collections.sort(results, new Comparator<PlaceScore>() {
               @Override
               public int compare(PlaceScore ps1, PlaceScore ps2) {
                  // make sort order deterministic
                  if (ps2.getScore() == ps1.getScore()) {
                     return ps1.getPlace().getId() < ps2.getPlace().getId() ? -1 : 1;
                  }
                  return Double.compare(ps2.getScore(), ps1.getScore());
               }
            });

            // remove lowest-scoring results
            while (results.size() > numResults) {
               results.remove(results.size()-1);
            }

            if (errorHandler != null && !errorLogged) {
               errorHandler.ambiguous(text, levelWords, currentIds, results.get(0).getPlace());
               errorLogged = true;
            }
         }
         else {
            Place p = getPlace(currentIds.get(0));
            results.add(new PlaceScore(p, scoreMatch(currentNameToken, p)));
         }
      }

      // in NEW mode, return "next-to-last-level-found, best match" if we didn't match the last level
      if (results.size() > 0 && mode == Mode.NEW && lastFoundLevel > 0) {
         Place p = new Place();
         p.setStandardizer(this);
         p.setName(generatePlaceName(levelWords.get(lastFoundLevel-1)));
         p.setLocatedInId(results.get(0).getPlace().getId());
         results.clear();
         results.add(new PlaceScore(p, 0));
      }

      return results;
   }

   public List<PlaceScore> standardize(String text, int numResults) {
      return standardize(text, null, Mode.BEST, numResults);
   }

   public Place standardize(String text) {
      return standardize(text, null);
   }

   public Place standardize(String text, String defaultCountry) {
      List<PlaceScore> results = standardize(text, defaultCountry, Mode.BEST, 1);
      return results.size() > 0 ? results.get(0).getPlace() : null;
   }
}
