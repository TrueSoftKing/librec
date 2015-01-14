## The Changes of the LibRec Library

## librec-v1.3 (Targets)
* New recommendation methods
  * CoFiSet
* New context-aware recommenders

### librec-v1.2 (Under Development)

* Support
  * Rating predictions can be outputed now (is.prediction.out), thanks to [disc5](https://github.com/disc5)'s comment. 
  * Data conversion from real-valued ratings to binary ones (val.binary.threshold)
  * A number of [command line options](http://www.librec.net/tutorial.html#cmd): 
    * -c configFile: set alternative configuration files,  *java -jar librec.jar -c yourConfigFile.conf*; 
    * -v/--version: print out version information
    * --dataset-spec: print out dataset specifications
* New recommendation methods implemented:
  * SLIM, FISM, SBPR, GBPR, TrustSVD
* New recommendation methods under testing:
  * timeSVD++
* Interface for context-aware recommender systems added
  * Context, UserContext, ItemContext, RatingContext  -- Data Class
  * ContextRecommender -- Generic Interface
* Others
  * Codes refactored & improved
  * SortMap performance improved, depency library updated
  * Rename BRPMF to BPR
  * Utility methods (e.g., data standardization) added to data structure
  * bugs fixed
    * critical bug: set isCCSUsed = true (see SparseMatrix, copyCCS() method) when cloning rateMtrix for trainMatrix
    * partially thanks to [albe91](https://github.com/albe91)'s comment. 

### librec-v1.1

* New recommendation methods implemented: 
  * WRMF, AR, PD, RankALS, SoRec, SoReg, RSTE  
* Support a number of testing views of the testing set:
  * all: the ratings of all users are used. 
  * cold-start: the ratings of cold-start users who rated less than 5 items (in the training set) are used.
* Support two new validation methods:
  * Given N: For each user, N ratings will be preserved as training set, while the rest are used as test set. 
  * Given ratio: Similarly as Given N, a ratio of users' ratings will be used for training and others for testing. 
  * val.ratio: Its meaning is changed to the ratio of data for training, rather than the ratio of data for testing.
* Data Structure:
  * DiagMatrix: diagonal matrix added
  * DataConvertor: is added to convert data files from one format to our supporting formats. 
  * A number of enhancement functions are added to matrix, vector class
* Package Refactor:
  * Package *librec.core* is split into two packages: 
    * librec.rating: algorithms for rating predictions; supporting rating-based item ranking. 
    * librec.ranking: algorithms for item ranking. 
* Others
  * Code improved
  * Some bugs fixed

### librec-v1.0

* A set of recommendations have been implemented. 
