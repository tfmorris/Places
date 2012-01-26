The purpose of this project is to match place texts to a database of current and historical
standardized places that includes geo-position information.

__[Try the demo](http://vivid-journey-2382.herokuapp.com/)__.

Database
--------

The database contains over 400,000 current and historical populated places and
higher-level political jurisdictions (districts, counties, provinces, states, etc.).
It is based upon the place wiki pages at [WeRelate.org](http://www.werelate.org/).
The database includes the place name, type (e.g., city, county, etc.) alternate
names, the jurisdictional hierarchy that was in place in the early 1900's,
earlier and later jurisdictional hierarchies, and geo-position coordinates.

Of course, the database is a long way from complete.  WeRelate contributors
continue to improve the database over time.  Updates to the places at WeRelate
will be added to the database periodically.

Matching algorithm
------------------

The matching algorithm is less than 1000 lines of code. It's written in Java, but
could be ported to other languages.  It basically tries matching places right to left,
looking for sub-jurisdictions of previously-matched levels and skipping intermediate
levels if not found.  It can match place texts even if the text doesn't include
commas between levels.  The algorithm is fast, matching about 100K places per second
on a single thread.

The algorithm has three modes:

* _BEST_ - get the closest place; if you can't match the left-most level, return
the lowest level that you matched,
* _REQUIRED_ - if you can't match the left-most level, don't return anything,
* _NEW_ - if you can't match the next level to the left, return a fake place name
containing the unmatched level to the left followed by the matched level to the right.

The last mode is useful for returning places that are potentially missing in the database.

Comparison
----------

FamilySearch has [a similar algorithm](https://labs.familysearch.org/stdfinder/PlaceStandardLookup.jsp),
but it is not open-source.  In a test of 3736 place texts, both systems standardize
just over 50% of texts identically.  An analysis of 38 texts that were standardized
differently show that both systems made about the same number of mistakes on that
small sample.
[Detailed results are shown here](https://github.com/DallanQ/Places/wiki/Comparison-to-FamilySearch).

Interestingly, this finding is similar to Nature's finding that the community-created Wikipedia
and the professionally-managed Encyclopedia Britannica had roughly the same number of errors.

Building
--------

You'll need maven. `mvn install` creates the normal jar files as well as ones with all dependencies

Tools
-----

* _AnalyzeMatches.java_ standardizes a file of place texts and counts the number of matches by country and level.

* _AnalyzePlaces.java_ analyzes a file of place texts and reports various statistics.

* _CompareMatches.java_ compares how this system standardizes a file of place texts to another.

* _StandardizePlaces.java_ standarizes a file of place texts and reports various types of problems in standardization.

* _Service module_ provides a simple REST-based interface to the place standardizer.

The tools (except for the service of course) can be run using
`mvn exec:java -Dexec.mainClass=org.folg.places.tools.<tool name> -Dexec.args="<args>"`

The service module generates a war file that can be run using tomcat, jetty, etc.

Other resources
---------------

The project also includes a list of around 7M place texts extracted from 7000 GEDCOMs
submitted to WeRelate.org over the past 5 years. If you want to try developing your
own matching algorithm, feel free to use these texts as test data.

Caveat: due to privacy concerns, place texts containing numbers (about 400K) were
removed from the data set.

Roadmap
-------

There are three ways in which this project could be improved upon:

* _Learn weights for scoring ambiguous matches_ - When a text matches multiple
places, which is the most likely?  Currently the project includes hand-generated
weights to score matching places.  Ideally people would label which of the
ambiguous places was most likely, and new weights would be learned based upon
the labeled data.

* _Learn from differences with FamilySearch_ - As mentioned above, when compared
against FamilySearch's place standard, sometimes the place matched by this project
is better, sometimes the place matched by FamilySearch is better.  Someone could
analyze the differences and review through the cases where FamilySearch was better,
adding alternate names and new places to the WeRelate place wiki when necessary.

* _Investiate frequent place texts that aren't matched_ - As new GEDCOMs get uploaded
to WeRelate, we can track which place texts in those GEDCOMs don't get matched.
Someone could review the frequent non-matching place texts and create pages for them
on the WeRelate place wiki if they are indeed real places.

Other projects
--------------

Check out [other genealogy projects[(https://github.com/DallanQ)
