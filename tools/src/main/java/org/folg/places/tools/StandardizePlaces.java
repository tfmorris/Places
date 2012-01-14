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

import org.folg.places.standardize.ErrorHandler;
import org.folg.places.standardize.Place;
import org.folg.places.standardize.Standardizer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXParseException;

import java.io.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * User: dallan
 * Date: 1/11/12
 */
public class StandardizePlaces implements ErrorHandler {
   private static Logger logger = Logger.getLogger("org.folg.places.standardize");

   @Option(name = "-i", required = true, usage = "places file in")
   private File placesIn;

   @Option(name = "-o", required = false, usage = "standardized places out")
   private File placesOut = null;

   @Option(name = "-a", required = false, usage = "print also-located-in places")
   private boolean printAlsoLocatedIns = false;

   @Option(name = "-n", required = false, usage = "number of places to standardize")
   private int maxPlaces = 0;

   @Option(name = "-t", required = false, usage = "number of results to show.  default is to only show the top result")
   private int numResults = 0;

   @Option(name = "-ao", required = false, usage = "ambiguous places out")
   private File ambiguousOut = null;

   @Option(name = "-mo", required = false, usage = "missing places out")
   private File missingOut = null;

   @Option(name = "-po", required = false, usage = "missing phrases out")
   private File phraseOut = null;

   @Option(name = "-to", required = false, usage = "missing types out")
   private File typeOut = null;

   @Option(name = "-no", required = false, usage = "not found places out")
   private File notFoundOut = null;

   @Option(name = "-so", required = false, usage = "skipped levels out")
   private File skippedOut = null;

   private Standardizer standardizer;
   private PrintWriter ambiguousWriter = null;
   private PrintWriter missingWriter = null;
   private PrintWriter phraseWriter = null;
   private PrintWriter typeWriter = null;
   private PrintWriter notFoundWriter = null;
   private PrintWriter skippedWriter = null;

   public StandardizePlaces() {
      standardizer = Standardizer.getInstance();
      standardizer.setErrorHandler(this);
   }

   private String generatePlaceName(List<List<String>> levels, int levelNumber) {
      StringBuilder buf = new StringBuilder();
      for (int i = levelNumber; i < levels.size(); i++) {
         if (buf.length() > 0) {
            buf.append(", ");
         }
         buf.append(standardizer.generatePlaceName(levels.get(i)));
      }
      return buf.toString();
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

   private static boolean hasDigit(List<String> words) {
      for (String word : words) {
         if (hasDigit(word)) {
            return true;
         }
      }
      return false;
   }

   @Override
   public void tokenNotFound(String text, List<List<String>> levels, int levelNumber, List<Integer> matchedParentIds) {
      List<String> words = levels.get(levelNumber);

      // log only tokens without numbers
      if (words.size() > 0 && !hasDigit(words)) {
         String levelName = standardizer.generatePlaceName(words);
         if (missingWriter != null && matchedParentIds != null && matchedParentIds.size() == 1) {
            // we don't want to create places for every single church and hospital
            if (!levelName.endsWith(" Church") && !levelName.endsWith(" Hospital")) {
               String fullName = levelName +", "+
                                 standardizer.getPlace(matchedParentIds.get(0)).getFullName();
               missingWriter.println(text+" | "+fullName);
            }
         }
         if (phraseWriter != null) {
            phraseWriter.println(levelName);
         }
      }
   }

   @Override
   public void skippingParentLevel(String text, List<List<String>> levels, int levelNumber, List<Integer> matchedPlaceIds) {
      List<String> words = levels.get(levelNumber);

      // log only children without numbers
      if (words.size() > 0 && !hasDigit(words)) {
         // build place with all levels from here up
         String hereUp = generatePlaceName(levels, levelNumber);
         // just write out the first place in the list
         Place p = standardizer.getPlace(matchedPlaceIds.get(0));
         skippedWriter.println(hereUp+" | "+p.getFullName());
      }
   }

   @Override
   public void typeNotFound(String text, List<List<String>> levels, int levelNumber, List<Integer> matchedPlaceIds) {
      if (typeWriter != null) {
         typeWriter.println(text);
      }
   }

   @Override
   public void ambiguous(String text, List<List<String>> levels, List<Integer> matchedPlaceIds, Place topPlace) {
      if (ambiguousWriter != null) {
         ambiguousWriter.println(text+" | "+topPlace.getFullName());
      }
   }

   @Override
   public void placeNotFound(String text, List<List<String>> levels) {
      if (notFoundWriter != null) {
         notFoundWriter.println(text);
      }
   }

   private void doMain() throws SAXParseException, IOException {
      BufferedReader bufferedReader = new BufferedReader(new FileReader(placesIn));
      PrintWriter placesWriter = placesOut != null ? new PrintWriter(placesOut) : new PrintWriter(System.out);
      if (ambiguousOut != null) {
         ambiguousWriter = new PrintWriter(ambiguousOut);
      }
      if (missingOut != null) {
         missingWriter = new PrintWriter(missingOut);
      }
      if (phraseOut != null) {
         phraseWriter = new PrintWriter(phraseOut);
      }
      if (typeOut != null) {
         typeWriter = new PrintWriter(typeOut);
      }
      if (notFoundOut != null) {
         notFoundWriter = new PrintWriter(notFoundOut);
      }
      if (skippedOut != null) {
         skippedWriter = new PrintWriter(skippedOut);
      }

      int lineCount = 0;
      while (bufferedReader.ready()) {
         String nextLine = bufferedReader.readLine();

         if (numResults == 0) {
            Place p = standardizer.standardize(nextLine);
            if (p != null) {
               placesWriter.println(nextLine + " | "+ p.getFullName());
               printAlsoLocatedIns(placesWriter, p);
            }
         }
         else {
            List<Standardizer.PlaceScore> results = standardizer.standardize(nextLine, numResults);
            placesWriter.println(nextLine);
            for (Standardizer.PlaceScore ps : results) {
               placesWriter.println("\t" + ps.getPlace().getFullName());
               printAlsoLocatedIns(placesWriter, ps.getPlace());
            }
         }

         if (lineCount % 100000 == 0) {
            System.out.print(".");
         }
         if (++lineCount == maxPlaces) {
            break;
         }
      }

      bufferedReader.close();
      placesWriter.close();

      if (ambiguousWriter != null) {
         ambiguousWriter.close();
      }
      if (missingWriter != null) {
         missingWriter.close();
      }
      if (phraseWriter != null) {
         phraseWriter.close();
      }
      if (typeWriter != null) {
         typeWriter.close();
      }
      if (notFoundWriter != null) {
         notFoundWriter.close();
      }
      if (skippedWriter != null) {
         skippedWriter.close();
      }
   }

   private void printAlsoLocatedIns(PrintWriter placesWriter, Place p) {
      int[] alsoLocatedInIds = p.getAlsoLocatedInIds();
      if ((printAlsoLocatedIns) && (alsoLocatedInIds != null) && (alsoLocatedInIds.length > 0)) {
         StringBuffer alsoLocatedStrs = new StringBuffer();
         for (int indx = 0; indx < alsoLocatedInIds.length; indx++) {
            int alsoLocatedInId = alsoLocatedInIds[indx];
            Place alsoLocatedPlace = standardizer.getPlace(alsoLocatedInId);
            if (alsoLocatedStrs.length() > 0) {
               alsoLocatedStrs.append(", ");
            }
            alsoLocatedStrs.append(alsoLocatedPlace.getFullName());
         }
         placesWriter.println("\talso located in = " + alsoLocatedStrs);

      }
   }

   public static void main(String[] args) throws SAXParseException, IOException {
      StandardizePlaces self = new StandardizePlaces();
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
