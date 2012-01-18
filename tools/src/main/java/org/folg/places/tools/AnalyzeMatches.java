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

package org.folg.places.tools;

import org.folg.places.standardize.Place;
import org.folg.places.standardize.Standardizer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXParseException;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * User: dallan
 * Date: 1/14/12
 */
public class AnalyzeMatches {
   private static Logger logger = Logger.getLogger("org.folg.places.tools");

   @Option(name = "-i", required = true, usage = "places file in")
   private File placesIn;

   @Option(name = "-o", required = true, usage = "counts file out")
   private File countsOut = null;

   private Standardizer standardizer;

   static class MatchCount {
      int countryId;
      int[] levelCounts;

      MatchCount() {
         countryId = 0;
         levelCounts = new int[Standardizer.MAX_LEVELS];
         for (int i = 0; i < levelCounts.length; i++) {
            levelCounts[i] = 0;
         }
      }
   }

   public AnalyzeMatches() {
      standardizer = Standardizer.getInstance();
   }

   private void doMain() throws SAXParseException, IOException {
      BufferedReader reader = new BufferedReader(new FileReader(placesIn));
      Map<String,MatchCount> matchCounts = new TreeMap<String, MatchCount>();

      // standardize all places + calculate matchCounts
      while (reader.ready()) {
         String nextLine = reader.readLine();
         Place p = standardizer.standardize(nextLine);
         if (p != null) {
            int level = p.getLevel();
            int countryId = p.getCountry();
            String fullName = p.getFullName();
            int pos = fullName.lastIndexOf(",");
            String countryName;
            if (pos >= 0) {
               countryName = fullName.substring(pos+1).trim();
            }
            else {
               countryName = fullName;
            }

            MatchCount matchCount = matchCounts.get(countryName);
            if (matchCount == null) {
               matchCount = new MatchCount();
               matchCount.countryId = countryId;
               matchCounts.put(countryName, matchCount);
            }
            matchCount.levelCounts[Math.min(Standardizer.MAX_LEVELS,level)-1]++;
         }
      }

      // generate the match counts file
      PrintWriter writer = new PrintWriter(countsOut);
      for (String countryName : matchCounts.keySet()) {
         MatchCount mc = matchCounts.get(countryName);
         StringBuilder buf = new StringBuilder();
         int total = 0;
         for (int i = 0; i < Standardizer.MAX_LEVELS; i++) {
            buf.append(",");
            total += mc.levelCounts[i];
            buf.append(mc.levelCounts[i]);
         }
         writer.println(countryName+","+mc.countryId+","+total+buf.toString());
      }

      writer.close();
   }

   public static void main(String[] args) throws SAXParseException, IOException {
      AnalyzeMatches self = new AnalyzeMatches();
      CmdLineParser parser = new CmdLineParser(self);
      try {
         parser.parseArgument(args);
         self.doMain();
      } catch (CmdLineException e) {
         System.err.println(e.getMessage());
         parser.printUsage(System.err);
      }
   }
}
