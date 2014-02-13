package librec.core;

import librec.data.SparseMatrix;
import librec.intf.IterativeRecommender;

/**
 * Salakhutdinov and Mnih, <strong>Bayesian Probabilistic Matrix Factorization
 * using Markov Chain Monte Carlo</strong>, ICML 2008. <br/>
 * 
 * Matlab version is provided by the authors via <a
 * href="http://www.utstat.toronto.edu/~rsalakhu/BPMF.html">this link</a>.
 * 
 * @author guoguibing
 * 
 */
public class BPMF extends IterativeRecommender {

	public BPMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "BayesianPMF";
	}

}
