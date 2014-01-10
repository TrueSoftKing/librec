package lib.rec.core;

import lib.rec.MatrixUtils;
import lib.rec.Recommender;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.MatrixEntry;
import no.uib.cipr.matrix.sparse.CompRowMatrix;

/**
 * Regularized SVD: <em>Arkadiusz Paterek, Improving Regularized Singular Value
 * Decomposition Collaborative Filtering, Proceedings of KDD Cup and Workshop,
 * 2007.</em>
 * 
 * @author guoguibing
 * 
 */
public class RegSVD extends Recommender {

	// learning rate 
	protected double lRate, momentum;
	// user and item regularization
	protected double regU, regI;
	// number of factors
	protected int numFactors;
	// number of iterations
	protected int maxIters;

	// whether to adjust learning rate automatically
	protected boolean isBoldDriver;

	// factorized user-factor matrix
	protected DenseMatrix P;

	// factorized item-factor matrix
	protected DenseMatrix Q;

	// training errors
	protected double errs, last_errs = 0;
	// objective loss
	protected double loss, last_loss = 0;

	public RegSVD(CompRowMatrix rm, CompRowMatrix tm, int fold) {
		super(rm, tm, fold);

		algoName = "RegSVD";

		lRate = cf.getDouble("val.learn.rate");
		momentum = cf.getDouble("val.momentum");
		regU = cf.getDouble("val.reg.user");
		regI = cf.getDouble("val.reg.item");

		numFactors = cf.getInt("num.factors");
		maxIters = cf.getInt("num.max.iter");

		isBoldDriver = cf.isOn("is.bold.driver");
	}

	@Override
	public void initModel() {

		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numItems, numFactors);

		// initialize model
		MatrixUtils.init(P, initMean, initStd);
		MatrixUtils.init(Q, initMean, initStd);

		// set to 0 for users without any ratings
		for (int u = 0, um = P.numRows(); u < um; u++) {
			if (MatrixUtils.row(trainMatrix, u).getUsed() == 0) {
				MatrixUtils.setOneValue(P, u, 0.0);
			}
		}
		// set to 0 for items without any ratings
		for (int j = 0, jm = Q.numRows(); j < jm; j++) {
			if (MatrixUtils.col(trainMatrix, j).getUsed() == 0) {
				MatrixUtils.setOneValue(Q, j, 0.0);
			}
		}
	}

	@Override
	public void buildModel() {

		for (int iter = 1; iter <= maxIters; iter++) {

			loss = 0;
			errs = 0;
			for (MatrixEntry me : trainMatrix) {

				int u = me.row(); // user
				int j = me.column(); // item

				double ruj = me.get();
				if (ruj <= 0.0)
					continue;

				double pred = predict(u, j, false);
				double euj = ruj - pred;

				errs += euj * euj;
				loss += euj * euj;

				// update factors
				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf - regU * puf;
					double delta_j = euj * puf - regI * qjf;

					P.add(u, f, lRate * delta_u);
					Q.add(j, f, lRate * delta_j);

					loss += regU * puf * puf + regI * qjf * qjf;
				}

			}

			errs *= 0.5;
			loss *= 0.5;

			if (postEachIter(iter))
				break;

		}// end of training

	}

	@Override
	protected double predict(int u, int j) {
		return MatrixUtils.rowMult(P, u, Q, j);
	}

	/**
	 * Post each iteration, we do things:
	 * 
	 * <ol>
	 * <li>print debug information</li>
	 * <li>adjust learning rate</li>
	 * <li>check if converged</li>
	 * </ol>
	 * 
	 * @param iter
	 *            current iteration
	 * 
	 * @return boolean: true if it is converged; false otherwise
	 * 
	 */
	protected boolean postEachIter(int iter) {

		if (verbose) {
			String foldInfo = fold > 0 ? " fold [" + fold + "]" : "";
			Logs.debug("{}{} iter {}: errs = {}, delta_errs = {}, loss = {}, delta_loss = {}, learn_rate = {}",
					new Object[] { algoName, foldInfo, iter, (float) errs, (float) (last_errs - errs), (float) loss,
							(float) (last_loss - loss), (float) lRate });
		}

		if (Double.isNaN(loss)) {
			Logs.error("Loss = NaN: current settings cannot train the recommender! Try other settings instead!");
			System.exit(-1);
		}

		// more ways to adapt learning rate can refer to: http://www.willamette.edu/~gorr/classes/cs449/momrate.html
		if (isBoldDriver && last_loss != 0.0)
			lRate = Math.abs(last_loss) > Math.abs(loss) ? lRate * 1.03 : lRate * 0.5;

		last_loss = loss;

		// check if converged
		boolean cond1 = (errs < 1e-5);
		boolean cond2 = (last_errs >= errs && last_errs - errs < 1e-5);
		last_errs = errs;

		return (cond1 || cond2) ? true : false;
	}

	@Override
	public String toString() {
		double learnRate = cf.getDouble("val.learn.rate"); // re-get initial learn rate in case bold driver is used. 
		return Strings.toString(new Object[] { learnRate, regU, regI, numFactors, maxIters, isBoldDriver }, ",");
	}

}
