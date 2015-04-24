// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.intf;

import happy.coding.io.FileIO;
import happy.coding.io.LineConfiger;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;

/**
 * Recommenders using iterative learning techniques
 * 
 * @author guoguibing
 * 
 */
public abstract class IterativeRecommender extends Recommender {

	/************************************ Static parameters for all recommenders ***********************************/
	// init, maximum learning rate, momentum
	protected static float initLRate, maxLRate, momentum;
	// line configer for regularization parameters
	protected static LineConfiger regOptions;
	// user, item and bias regularization
	protected static float regU, regI, regB, reg;
	// number of factors
	protected static int numFactors;
	// number of iterations
	protected static int numIters;

	// whether to adjust learning rate automatically
	protected static boolean isBoldDriver;
	// decay of learning rate
	protected static float decay;

	/************************************ Recommender-specific parameters ****************************************/
	// factorized user-factor matrix
	protected DenseMatrix P;

	// factorized item-factor matrix
	protected DenseMatrix Q;

	// user biases
	protected DenseVector userBias;
	// item biases
	protected DenseVector itemBias;

	// adaptive learn rate
	protected double lRate;
	// training errors
	protected double errs, last_errs = 0;
	// objective loss
	protected double loss, last_loss = 0;

	// initial models using normal distribution
	protected boolean initByNorm;

	// initialization
	static {
		LineConfiger lc = cf.getParamOptions("learn.rate");
		initLRate = Float.parseFloat(lc.getMainParam());
		maxLRate = lc.getFloat("-max", -1);
		isBoldDriver = lc.contains("-bold-driver");
		decay = lc.getFloat("-decay", -1);
		momentum = lc.getFloat("-momentum", 50);

		regOptions = cf.getParamOptions("reg.lambda");
		reg = Float.parseFloat(regOptions.getMainParam());
		regU = regOptions.getFloat("-u", reg);
		regI = regOptions.getFloat("-i", reg);
		regB = regOptions.getFloat("-b", reg);

		numFactors = cf.getInt("num.factors");
		numIters = cf.getInt("num.max.iter");
	}

	public IterativeRecommender(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		lRate = initLRate;
		initByNorm = true;
	}

	/**
	 * default prediction method
	 */
	@Override
	protected double predict(int u, int j) throws Exception {
		return DenseMatrix.rowMult(P, u, Q, j);
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
			String learnRate = lRate > 0 ? ", learn_rate = " + (float) lRate : "";
			Logs.debug("{}{} iter {}: errs = {}, delta_errs = {}, loss = {}, delta_loss = {}{}",
					new Object[] { algoName, foldInfo, iter, (float) errs, (float) (last_errs - errs), (float) loss,
							(float) (Math.abs(last_loss) - Math.abs(loss)), learnRate });
		}

		if (Double.isNaN(loss) || Double.isInfinite(loss)) {
			Logs.error("Loss = NaN or Infinity: current settings does not fit the recommender! Change the settings and try again!");
			System.exit(-1);
		}

		// check if converged
		boolean cond1 = Math.abs(errs) < 1e-5;
		boolean cond2 = (last_errs >= errs && last_errs - errs < 1e-5);
		boolean converged = cond1 || cond2;

		// if not converged, update learning rate
		if (!converged)
			updateLRate(iter);

		last_loss = loss;
		last_errs = errs;

		return converged;
	}

	/**
	 * Update current learning rate after each epoch <br/>
	 * 
	 * <ol>
	 * <li>bold driver: Gemulla et al., Large-scale matrix factorization with distributed stochastic gradient descent,
	 * KDD 2011.</li>
	 * <li>constant decay: Niu et al, Hogwild!: A lock-free approach to parallelizing stochastic gradient descent, NIPS
	 * 2011.</li>
	 * <li>Leon Bottou, Stochastic Gradient Descent Tricks</li>
	 * <li>more ways to adapt learning rate can refer to: http://www.willamette.edu/~gorr/classes/cs449/momrate.html</li>
	 * </ol>
	 * 
	 * @param iter
	 *            the current iteration
	 */
	protected void updateLRate(int iter) {

		if (lRate <= 0)
			return;

		if (isBoldDriver && iter > 1)
			lRate = Math.abs(last_loss) > Math.abs(loss) ? lRate * 1.05 : lRate * 0.5;
		else if (decay > 0 && decay < 1)
			lRate *= decay;

		// limit to max-learn-rate after update
		if (maxLRate > 0 && lRate > maxLRate)
			lRate = maxLRate;
	}

	@Override
	protected void initModel() throws Exception {

		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numItems, numFactors);

		// initialize model
		if (initByNorm) {
			P.init(initMean, initStd);
			Q.init(initMean, initStd);
		} else {
			P.init(); // P.init(smallValue);
			Q.init(); // Q.init(smallValue);
		}

	}

	protected void saveModel() throws Exception {
		// make a folder
		String dirPath = FileIO.makeDirectory(tempDirPath, algoName);

		// suffix info
		String suffix = foldInfo + ".bin";

		// writing training, test data
		FileIO.serialize(trainMatrix, dirPath + "trainMatrix" + suffix);
		FileIO.serialize(testMatrix, dirPath + "testMatrix" + suffix);

		// write matrices P, Q
		FileIO.serialize(P, dirPath + "userFactors" + suffix);
		FileIO.serialize(Q, dirPath + "itemFactors" + suffix);

		// write vectors
		if (userBias != null)
			FileIO.serialize(userBias, dirPath + "userBiases" + suffix);
		if (itemBias != null)
			FileIO.serialize(itemBias, dirPath + "itemBiases" + suffix);

		Logs.debug("Learned models are saved to folder \"{}\"", dirPath);
	}

	protected void loadModel() throws Exception {
		// make a folder
		String dirPath = FileIO.makeDirectory(tempDirPath, algoName);

		Logs.debug("A recommender model is loaded from {}", dirPath);

		// suffix info
		String suffix = foldInfo + ".bin";

		trainMatrix = (SparseMatrix) FileIO.deserialize(dirPath + "trainMatrix" + suffix);
		testMatrix = (SparseMatrix) FileIO.deserialize(dirPath + "testMatrix" + suffix);

		// write matrices P, Q
		P = (DenseMatrix) FileIO.deserialize(dirPath + "userFactors" + suffix);
		Q = (DenseMatrix) FileIO.deserialize(dirPath + "itemFactors" + suffix);

		// write vectors
		userBias = (DenseVector) FileIO.deserialize(dirPath + "userBiases" + suffix);
		itemBias = (DenseVector) FileIO.deserialize(dirPath + "itemBiases" + suffix);
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { initLRate, maxLRate, regB, regU, regI, numFactors, numIters,
				isBoldDriver }, ",");
	}

}
