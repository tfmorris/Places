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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: dallan
 * Date: 1/10/12
 */
@XmlRootElement
public class Place {
   private int id = 0;
   private String name = null;
   private String[] altNames = null;
   private String[] types = null;
   private int locatedInId = 0;
   private int[] alsoLocatedInIds = null;
   private int level = 0;
   private int country = 0;
   private double latitude = 0.0;
   private double longitude = 0.0;
   private Standardizer standardizer = null;

   public int getId() {
      return id;
   }

   public void setId(int id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String[] getAltNames() {
      return altNames;
   }

   public void setAltNames(String[] altNames) {
      this.altNames = altNames;
   }

   public String[] getTypes() {
      return types;
   }

   public void setTypes(String[] types) {
      this.types = types;
   }

   public int getLocatedInId() {
      return locatedInId;
   }

   public void setLocatedInId(int locatedInId) {
      this.locatedInId = locatedInId;
   }

   public int[] getAlsoLocatedInIds() {
      return alsoLocatedInIds;
   }

   public void setAlsoLocatedInIds(int[] alsoLocatedInIds) {
      this.alsoLocatedInIds = alsoLocatedInIds;
   }

   public int getLevel() {
      return level;
   }

   public void setLevel(int level) {
      this.level = level;
   }

   public int getCountry() {
      return country;
   }

   public void setCountry(int country) {
      this.country = country;
   }

   public double getLatitude() {
      return latitude;
   }

   public void setLatitude(double latitude) {
      this.latitude = latitude;
   }

   public double getLongitude() {
      return longitude;
   }

   public void setLongitude(double longitude) {
      this.longitude = longitude;
   }

   void setStandardizer(Standardizer standardizer) {
      this.standardizer = standardizer;
   }

   @XmlElement
   public String getFullName() {
      StringBuilder buf = new StringBuilder();
      if (standardizer != null) {
         buf.append(getName());
         int locatedIn = getLocatedInId();
         while (locatedIn > 0) {
            Place p = standardizer.getPlace(locatedIn);
            buf.append(", ");
            buf.append(p.getName());
            locatedIn = p.getLocatedInId();
         }
      }
      return buf.toString();
   }
}
