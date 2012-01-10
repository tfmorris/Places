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

import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXParseException;

import java.io.*;

/**
 * User: RyanK
 * Date: 1/1/12
 */
public class AnalyzePlaces {

    @Option(name = "-i", required = true, usage = "places file in")
    private File placesIn;

    @Option(name = "-o", required = false, usage = "directory for analysis file output")
   private File analysisPlacesOut;

    // break apart words, so North Grinston is split into separate words
    private static String SPLIT_REGEX = "[, ]+";

    private int REVERSE_EVERY_N = 10;

   private CountsCollector placesCountCC;
   private int totalPlacesCount;

   private CountsCollector wordsCountCC;
   private int totalWordsCount;

    private CountsCollector numbersCountCC;
    private int totalNumbersCount;

    private CountsCollector endingsOfPlacesCC;
    private int endingsOfPlacesTotalCount;

    public AnalyzePlaces() {
       placesCountCC = new CountsCollector();
       totalPlacesCount = 0;

       wordsCountCC = new CountsCollector();
       totalWordsCount = 0;

        numbersCountCC = new CountsCollector();
        totalNumbersCount = 0;

        endingsOfPlacesCC = new CountsCollector();
        endingsOfPlacesTotalCount = 0;

    }

    private void doMain() throws SAXParseException, IOException {

        PrintWriter reversedWordsWriter = analysisPlacesOut != null ? new PrintWriter(new File(analysisPlacesOut, "reversedWords.txt")) : new PrintWriter(System.out);

        BufferedReader bufferedReader = new BufferedReader(new FileReader(placesIn));

        int lineCount = 0;
        while (bufferedReader.ready()) {
            String nextLine = bufferedReader.readLine();
            nextLine = nextLine.trim().toLowerCase();
            if (nextLine.length() == 0)
                continue;

            lineCount++;
            if (lineCount % 5000 == 0)
                System.out.println("indexing line " + lineCount);

            placesCountCC.add(nextLine);
            totalPlacesCount++;

            String[] placeList = nextLine.split(SPLIT_REGEX);

            for (String place : placeList) {
                place = place.trim();

                if (place.length() == 0)
                    continue;

                if (NumberUtils.isNumber(place)) {
                    numbersCountCC.add(place);
                    totalNumbersCount++;
                } else {
                    wordsCountCC.add(place);
                    totalWordsCount++;
                }
            }

            int lastCommaIndx = nextLine.lastIndexOf(",");
            String lastWord = nextLine.substring(lastCommaIndx + 1).trim();
            if (lastWord.length() > 0) {
                endingsOfPlacesCC.add(lastWord);
                endingsOfPlacesTotalCount++;
            }

            if (lineCount % REVERSE_EVERY_N == 0) {
                StringBuilder reversedWord = new StringBuilder(nextLine);
                reversedWordsWriter.println(reversedWord.reverse());
            }
        }

        System.out.println("total number of lines in files " + lineCount);

        System.out.println("Indexed a total of " + totalPlacesCount + " places.");
        System.out.println("Found a total of " + getPlacesCountCC().size() + " unique places.");
        getPlacesCountCC().writeSorted(false, 1, analysisPlacesOut != null ? new PrintWriter(new File(analysisPlacesOut, "placesCount.txt")) : new PrintWriter(System.out));

        System.out.println("Indexed a total of " + totalWordsCount + " words.");
        System.out.println("Found a total of " + getWordsCountCC().size() + " unique words.");
        getWordsCountCC().writeSorted(false, 1, analysisPlacesOut != null ? new PrintWriter(new File(analysisPlacesOut, "wordsCount.txt")) : new PrintWriter(System.out));

        System.out.println("Indexed a total of " + totalNumbersCount + " numbers.");
        System.out.println("Found a total of " + getNumbersCountCC().size() + " unique numbers.");
        getNumbersCountCC().writeSorted(false, 1, analysisPlacesOut != null ? new PrintWriter(new File(analysisPlacesOut, "numbersCount.txt")) : new PrintWriter(System.out));


        System.out.println("Indexed a total of " + endingsOfPlacesTotalCount + " endings.");
        System.out.println("Found a total of " + getEndingsOfPlacesCC().size() + " unique endings.");
        getEndingsOfPlacesCC().writeSorted(false, 1, analysisPlacesOut != null ? new PrintWriter(new File(analysisPlacesOut, "endingsCount.txt")) : new PrintWriter(System.out));
    }

    public CountsCollector getPlacesCountCC() {
        return placesCountCC;
    }

    public CountsCollector getWordsCountCC() {
        return wordsCountCC;
    }

    public CountsCollector getNumbersCountCC() {
        return numbersCountCC;
    }

    public CountsCollector getEndingsOfPlacesCC() {
        return endingsOfPlacesCC;
    }

    public static void main(String[] args) throws SAXParseException, IOException {
        AnalyzePlaces self = new AnalyzePlaces();
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
