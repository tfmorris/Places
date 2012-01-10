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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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

   private Set<String> unindexedWords = null;
   private Map<Integer,Place> placeIndex = null;
   private Map<String,Integer[]> wordIndex = null;
   private DataSource dataSource = null;
   private MemcachedClient memcachedClient = null;
   private String memcacheKeyPrefix = null;
   private int memcacheExpiration = 0;

   private Standardizer() {
      Reader indexReader = null;

      try {
         // read properties
         Properties props = new Properties();
         props.load(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("standardizer.properties"), "UTF8"));
         unindexedWords = new HashSet<String>(Arrays.asList(props.getProperty("unindexedWords").split(",")));

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
      // TODO
   }

   /**
    * Read the place index
    * You would not normally call this function. Used in testing and evaluation
    */
   public void readPlaceIndex(Reader reader) throws IOException {
      // TODO
   }

   private List<Integer> lookupWord(String word) {
      // TODO
      return null;
   }

   private List<Place> lookupPlaceIds(List<Integer> ids) {
      // TODO
      return null;
   }

   public StandardizeResult standardize(String text) {
      return standardize(text, null);
   }

   public StandardizeResult standardize(String text, String defaultCountry) {
      // TODO
      return null;
   }

}
