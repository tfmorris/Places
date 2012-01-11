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
import java.util.logging.Logger;

/**
 * User: dallan
 * Date: 1/10/12
 */
public class Standardizer {
   private static Logger logger = Logger.getLogger("org.folg.places.standardize");
   private static Standardizer standardizer = new Standardizer();

   public static Standardizer getInstance() {
      return standardizer;
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
            logger.warning("Unable to initialize memcache client");
         }
      }
      return staticMC;
   }

   private Normalizer normalizer = null;
   private Set<String> typeWords = null;
   private Map<String,String> abbreviations = null;
   private Set<Integer> expectedCountries = null;
   private Map<Integer,Place> placeIndex = null;
   private Map<String,Integer[]> wordIndex = null;
   private DataSource dataSource = null;
   private MemcachedClient memcachedClient = null;
   private String memcacheKeyPrefix = null;
   private int memcacheExpiration = 0;

   private Standardizer() {
      normalizer = Normalizer.getInstance();

      Reader indexReader = null;

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

         // read expectedCountries
         expectedCountries = new HashSet<Integer>();
         for (String expectedCountry : props.getProperty("expectedCountries").split(",")) {
            expectedCountries.add(Integer.parseInt(expectedCountry));
         }

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
      }
      finally {
         try {
            if (indexReader != null) {
               indexReader.close();
            }
         }
         catch (IOException e) {
            // ignore
         }
      }
   }

   /**
    * Read the word index
    * You would not normally call this function. Used in testing and evaluation
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
    * You would not normally call this function. Used in testing and evaluation
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

   // return null if word not found
   private List<Integer> lookupWord(String word) {
      Integer[] ids = wordIndex.get(word);
      if (ids != null) {
         return Arrays.asList(ids);
      }
      return null;
   }

   public Place lookupPlace(int id) {
      Place p = placeIndex.get(id);
      if (p == null) {
         logger.severe("Place not found: "+id);
      }
      return p;
   }

   private boolean checkAncestorMatch(int id, List<Integer> ids) {
      Place p = lookupPlace(id);
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
         Place p = lookupPlace(id);
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
      int primaryNameMatch = (normalizedName.indexOf(nameToken) >= 0 ? 1 : 0);
      int level = p.getLevel();
      int expectedCountry = (expectedCountries.contains(p.getCountry()) ? 1 : 0);

      // TODO -- we need to learn these weights ideally, come up with better features, etc.
      return primaryNameMatch * 5 + (6 - level) * 10 + expectedCountry * 2;
   }

   // catenate all of the words together into one token, with ending type words in a second token
   private String[] getNameTypeToken(List<String> words) {
      StringBuilder buf = new StringBuilder();
      String[] result = new String[2];
      result[0] = null; // name token
      result[1] = null; // type token (optional)
      boolean foundNameWord = false;
      for (int i = words.size()-1; i >= 0; i--) {
         String word = words.get(i);
         if (word.length() > 0) {
            String expansion = abbreviations.get(word);
            if (expansion != null) {
               word = expansion;
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

   private static boolean hasDigit(String s) {
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c >= '0' && c <= '9') {
            return true;
         }
      }
      return false;
   }

   public Place standardize(String text, String defaultCountry) {
      List<List<String>> levelWords = normalizer.tokenize(text).getLevels();
      List<Integer> currentIds = null;
      String currentNameToken = null;

      for (int level = levelWords.size()-1; level >= 0; level--) {
         List<String> words = levelWords.get(level);
         String[] nameType = getNameTypeToken(words);

         // lookup name token
         List<Integer> ids = lookupWord(nameType[0]);

         // didn't find any matches; log and ignore for now
         if (ids == null) {
            if (!hasDigit(nameType[0])) {
               logger.info("Name not found: "+nameType[0]+" in: "+text);
            }
         }
         else {
            // if we found previous matches, filter subplaces
            boolean ignoreTypeToken = false;
            if (currentIds != null) {
               List<Integer> matchingIds = filterSubplaceMatches(ids, currentIds);
               // didn't find any children; log and ignore for now
               if (matchingIds.size() == 0) {
                  ignoreTypeToken = true;
                  logger.info("subplace not found: "+nameType[0]+" in: "+text);
               }
               else {
                  ids = matchingIds;
               }
            }

            // if we still have multiple matches, filter type
            if (ids.size() > 1 && nameType[1] != null && !ignoreTypeToken) {
               List<Integer> matchingIds = filterTypeMatches(nameType[1], ids);
               // didn't find a type match; log and ignore
               if (matchingIds.size() == 0) {
                  logger.info("type not found: "+nameType[1]+" in: "+text);
               }
               else {
                  ids = matchingIds;
               }
            }

            currentIds = ids;
            currentNameToken = nameType[0];
         }
      }

      Place result = null;
      // if we have no matches, return empty
      if (currentIds == null) {
         if (levelWords.size() > 0) {
            logger.info("no place found: "+text);
         }
         result = new Place();
      }
      else {
         // if we have multiple matches and a default country, filter subplaces of the default country
         if (currentIds.size() > 1 && defaultCountry != null && defaultCountry.length() > 0) {
            // TODO
         }

         // if we have still have multiple matches, score them and return the highest-scoring one
         if (currentIds.size() > 1) {
            double highestScore = Double.NEGATIVE_INFINITY;
            for (int id : currentIds) {
               Place p = lookupPlace(id);
               double score = scoreMatch(currentNameToken, p);
               if (score > highestScore) {
                  result = p;
                  highestScore = score;
               }
            }
         }
         else {
            result = lookupPlace(currentIds.get(0));
         }
      }

      // We don't normally set
      return result;
   }

   public Place standardize(String text) {
      return standardize(text, null);
   }
}
