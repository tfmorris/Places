package org.folg.places.standardize;

import java.util.List;

/**
 * User: ryan
 * Date: 1/10/12
 */
public class NormalizeResults {

   private String endingNumbers;
   private List<List<String>> levels;

   public NormalizeResults() {
      endingNumbers = null;
      levels = null;
   }

   public String getEndingNumbers() {
      return endingNumbers;
   }

   public void setEndingNumbers(String endingNumbers) {
      this.endingNumbers = endingNumbers;
   }

   public List<List<String>> getLevels() {
      return levels;
   }

   public void setLevels(List<List<String>> levels) {
      this.levels = levels;
   }
}
