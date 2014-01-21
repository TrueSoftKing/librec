package lib.rec.intf;

import happy.coding.io.Logs;
import happy.coding.io.Strings;
import lib.rec.data.DenseMat;
import lib.rec.data.SparseMat;

/**
 * Interface class for iterative recommenders such as Matrix Factorization
 * 
 * @author guoguibing
 * 
 */
public abstract class IterativeRecommender extends Recommender {

	// learning rate 
	protected double lRate, initLRate, momentum;
	// user and item regularization
	protected double regU, regI;
	// number of factors
	protected int numFactors;
	// number of iterations
	protected int maxIters;

	// whether to adjust learning rate automatically
	protected boolean isBoldDriver;
	// decay of learning rate
	protected double decay;

	// factorized user-factor matrix
	protected DenseMat P, last_P;

	// factorized item-factor matrix
	protected DenseMat Q, last_Q;

	// training errors
	protected double errs, last_errs = 0;
	// objective loss
	protected double loss, last_loss = 0;

	// fold information 
	protected String foldInfo;

	public IterativeRecommender(SparseMat trainMatrix, SparseMat testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		lRate = cf.getDouble("val.learn.rate");
		initLRate = lRate; // initial learn rate
		momentum = cf.getDouble("val.momentum");
		regU = cf.getDouble("val.reg.user");
		regI = cf.getDouble("val.reg.item");

		numFactors = cf.getInt("num.factors");
		maxIters = cf.getInt("num.max.iter");

		isBoldDriver = cf.isOn("is.bold.driver");
		decay = cf.getDouble("val.decay.rate");
		foldInfo = fold > 0 ? " fold [" + fold + "]" : "";
	}

	/**
	 * default prediction method
	 */
	@Override
	protected double predict(int u, int j) {
		return DenseMat.rowMult(P, u, Q, j);
	}

	/**
	 * Post each iteration, we do things:
	 * 
	 * <ol>
	 * <li>print debug information</li>
	 * <li>check if converged</li>
	 * <li>if not, adjust learning rate</li>
	 * </ol>
	 * 
	 * @param iter
	 *            current iteration
	 * 
	 * @return boolean: true if it is converged; false otherwise
	 * 
	 */
	protected boolean isConverged(int iter) {

		// print out debug info
		if (verbose) {
			Logs.debug("{}{} iter {}: errs = {}, delta_errs = {}, loss = {}, delta_loss = {}, learn_rate = {}",
					new Object[] { algoName, foldInfo, iter, (float) errs, (float) (last_errs - errs), (float) loss,
							(float) (Math.abs(last_loss) - Math.abs(loss)), (float) lRate });
		}

		if (Double.isNaN(loss)) {
			Logs.error("Loss = NaN: current settings cannot train the recommender! Try other settings instead!");
			System.exit(-1);
		}

		// check if converged
		boolean cond1 = (errs < 1e-5);
		boolean cond2 = (last_errs >= errs && last_errs - errs < 1e-5);
		boolean converged = cond1 || cond2;
		
		// if not converged, update learning rate
		if(!converged)
			updateLRate(iter);
		
		return converged;
	}

	/**
	 * Update current learning rate after each epoch <br/>
	 * 
	 * <ol>
	 * The update rules refers to:
	 * <li>bold driver: Gemulla et al., Large-scale matrix factorization with
	 * distributed stochastic gradient descent, ACM KDD 2011.</li>
	 * <li>constant decay: Niu et al, Hogwild!: A lock-free approach to
	 * parallelizing stochastic gradient descent, NIPS 2011.</li>
	 * <li>Leon Bottou, Stochastic Gradient Descent Tricks</li>
	 * <li>more ways to adapt learning rate can refer to:
	 * http://www.willamette.edu/~gorr/classes/cs449/momrate.html</li>
	 * </ol>
	 * 
	 * @param iter
	 *            the current iteration
	 */
	protected void updateLRate(int iter) {
		if (isBoldDriver && last_loss != 0.0) {
			if (Math.abs(last_loss) > Math.abs(loss)) {
				lRate *= 1.05;

				// update last weight changes
				last_P = P.copy();
				last_Q = Q.copy();
			} else {
				lRate *= 0.5;

				// undo last weight changes
				Logs.debug("{} iter {}: undo last weight changes and sharply decrease the learning rate !", foldInfo,
						iter);
				P = last_P.copy();
				Q = last_Q.copy();
				
				return; // do no update last loss and errors, since we discard current loss and errors. 
			}
		} else if (decay > 0 && decay < 1)
			lRate *= decay;
		else if (decay == 0)
			lRate = initLRate / (1 + initLRate * regU * iter);
		
		last_loss = loss;
		last_errs = errs;
	}

	@Override
	protected void initModel() {

		P = new DenseMat(numUsers, numFactors);
		Q = new DenseMat(numItems, numFactors);

		// initialize model
		P.init(initMean, initStd);
		Q.init(initMean, initStd);

		// set to 0 for users without any ratings
		int numTrainUsers = trainMatrix.numRows();
		for (int u = 0, um = P.numRows(); u < um; u++) {
			if (u >= numTrainUsers || trainMatrix.rowSize(u) == 0) {
				P.setRow(u, 0.0);
			}
		}
		// set to 0 for items without any ratings
		int numTrainItems = trainMatrix.numColumns();
		for (int j = 0, jm = Q.numRows(); j < jm; j++) {
			if (j >= numTrainItems || trainMatrix.colSize(j) == 0) {
				Q.setRow(j, 0.0);
			}
		}

		last_P = P.copy();
		last_Q = Q.copy();
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { initLRate, regU, regI, numFactors, maxIters, isBoldDriver }, ",");
	}

}
