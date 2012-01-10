/*
 * Copyright 2011 Ancestry.com and Foundation for On-Line Genealogy, Inc.
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Logger;

/**
 * Normalize a place text
 */
public class Normalizer {
   private static Logger logger = Logger.getLogger("org.folg.places.search");
   private static Normalizer normalizer = new Normalizer();
   private final Map<Character,String> characterReplacements;

   public static Normalizer getInstance() {
      return normalizer;
   }

   private Normalizer() {
      // read properties file
      try {
         Properties props = new Properties();
         props.load(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("normalizer.properties"), "UTF8"));
         characterReplacements = new HashMap<Character,String>();
         for (String replacement : props.getProperty("characterReplacements").split(",")) {
            characterReplacements.put(replacement.charAt(0), replacement.substring(2));
         }
      } catch (IOException e) {
         throw new RuntimeException("normalizer.properties not found");
      }
   }

   /**
    * Tokenize name by removing diacritics, lowercasing, splitting on delimiter, and removing non a-z characters
    * @param text string to tokenize
    * @return tokenized place levels
    */
   public List<String> tokenize(String text) {
      // remove diacritics, lowercase, split on delimiters, remove non-alphanumeric?
      List<String> levels = new ArrayList<String>();
      StringBuilder buf = new StringBuilder();

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         String replacement;
         if (c == ',') {
            if (buf.length() > 0) {
               levels.add(buf.toString());
               buf.setLength(0);
            }
         }
         else if ((replacement = characterReplacements.get(c)) != null) {
            buf.append(replacement.toLowerCase());
         }
         else if (c >= 'A' && c <= 'Z') {
            buf.append(Character.toLowerCase(c));
         }
         else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
            buf.append(c);
         }
         else if (Character.isLetter(c)) {
            // ignore letters > U+0250; they're generally from scripts that don't map well to roman letters
            // ignore 186,170: superscript o and a used in spanish numbers: 1^a and 2^o
            // ignore 440,439: Ezh and reverse-Ezh; the only times they appear in the data is in what appears to be noise
            if (c < 592 && c!=186 && c!=170 && c!=439 && c!=440) {
               logger.warning("Untokenized letter:"+c+" ("+(int)c+") in "+text);
            }
         }
      }
      if (buf.length() > 0) {
         levels.add(buf.toString());
      }
      return levels;
   }
}
