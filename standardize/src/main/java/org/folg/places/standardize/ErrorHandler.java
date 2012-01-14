package org.folg.places.standardize;


import java.util.List;

/**
 * User: dallan
 * Date: 1/12/12
 * 
 * Used to analyze the types of errors/warnings seen when standardizing places
 */
public interface ErrorHandler {
   public void tokenNotFound(String text, List<List<String>> levels, int levelNumber, List<Integer> matchedParentIds);
   public void skippingParentLevel(String text, List<List<String>> levels, int levelNumber, List<Integer> matchedPlaceIds);
   public void typeNotFound(String text, List<List<String>> levels, int levelNumber, List<Integer> matchedPlaceIds);
   public void ambiguous(String text, List<List<String>> levels, List<Integer> matchedPlaceIds, Place topPlace);
   public void placeNotFound(String text, List<List<String>> levels);
}
