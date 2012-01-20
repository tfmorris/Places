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
 * Compare matches with another system against ours
 * The input matches file should have the form place-text|matched-place
 * The output file contains differences: place-text|our-matched-place|their-matched-place
 *
 * User: dallan
 * Date: 1/20/12
 */
public class CompareMatches {
   private static Logger logger = Logger.getLogger("org.folg.places.tools");

   @Option(name = "-i", required = true, usage = "matches file in")
   private File matchesIn;

   @Option(name = "-o", required = true, usage = "disagreements file out")
   private File disagreementsOut = null;

   private Standardizer standardizer;

   public CompareMatches() {
      standardizer = Standardizer.getInstance();
   }

   private String removeSpuriousDifferences(String place) {
      // lowercase
      place = place.toLowerCase();
      // remove beginning and ending commas
      place = place.replaceFirst("^[, ]+", "").replaceFirst("[, ]+$", "");
      // remove parenthetical type names
      place = place.replaceAll("\\s*\\([^)]*\\)", "");
      return place;
   }

   private void doMain() throws SAXParseException, IOException {
      BufferedReader reader = new BufferedReader(new FileReader(matchesIn));
      PrintWriter writer = new PrintWriter(disagreementsOut);
      int cntDiffs = 0;
      int cntSame = 0;

      // standardize all places and compare
      while (reader.ready()) {
         String nextLine = reader.readLine();
         String[] fields = nextLine.split("\\|");
         String text = fields[0];
         String otherPlace = fields[1];
         String ourPlace = "";
         Place p = standardizer.standardize(text);
         if (p != null) {
            ourPlace = p.getFullName();
         }
         if (!removeSpuriousDifferences(ourPlace).equals(removeSpuriousDifferences(otherPlace))) {
            cntDiffs++;
            writer.println(text+"|"+ourPlace+"|"+otherPlace);
         }
         else {
            cntSame++;
         }
      }

      writer.close();
      reader.close();
      System.out.println("Total places="+(cntSame+cntDiffs)+" same="+cntSame+" different="+cntDiffs);
   }

   public static void main(String[] args) throws SAXParseException, IOException {
      CompareMatches self = new CompareMatches();
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
