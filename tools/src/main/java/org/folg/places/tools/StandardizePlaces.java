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
import java.util.List;

/**
 * User: dallan
 * Date: 1/11/12
 */
public class StandardizePlaces {

   @Option(name = "-i", required = true, usage = "places file in")
   private File placesIn;

   @Option(name = "-o", required = false, usage = "standardized places out")
   private File placesOut = null;

   @Option(name = "-n", required = false, usage = "number of places to standardize")
   private int maxPlaces = 0;

   @Option(name = "-t", required = false, usage = "number of results to show.  default is to only show the top result")
   private int numResults = 0;

   private Standardizer standardizer;

   private boolean printAlsoLocatedIns = true;

   public StandardizePlaces() {
      standardizer = Standardizer.getInstance();
   }

   private void doMain() throws SAXParseException, IOException {
      BufferedReader bufferedReader = new BufferedReader(new FileReader(placesIn));
      PrintWriter placesWriter = placesOut != null ? new PrintWriter(placesOut) : new PrintWriter(System.out);

      int lineCount = 0;
      while (bufferedReader.ready()) {
         String nextLine = bufferedReader.readLine();

         if (numResults == 0) {
            Place p = standardizer.standardize(nextLine);
            placesWriter.println(p.getFullName() + " | " + nextLine);
            printAlsoLocatedIns(placesWriter, p);
         }
         else {
            List<Place> results = standardizer.standardize(nextLine, numResults);
            placesWriter.println(nextLine);
            for (Place p : results) {
               placesWriter.println("\t" + p.getFullName());
               printAlsoLocatedIns(placesWriter, p);
            }

         }

         if (++lineCount == maxPlaces) {
            break;
         }
      }

      bufferedReader.close();
      placesWriter.close();
   }

   private void printAlsoLocatedIns(PrintWriter placesWriter, Place p) {
      int[] alsoLocatedInIds = p.getAlsoLocatedInIds();
      if ((printAlsoLocatedIns) && (alsoLocatedInIds != null) && (alsoLocatedInIds.length > 0)) {
         StringBuffer alsoLocatedStrs = new StringBuffer();
         for (int indx = 0; indx < alsoLocatedInIds.length; indx++) {
            int alsoLocatedInId = alsoLocatedInIds[indx];
            Place alsoLocatedPlace = standardizer.lookupPlace(alsoLocatedInId);
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
