package lib.rec.core;

import lib.rec.data.DenseMat;
import lib.rec.data.DenseVec;
import lib.rec.data.SparseMat;
import lib.rec.intf.IterativeRecommender;
import no.uib.cipr.matrix.MatrixEntry;

/**
 * Biased Matrix Factorization Models. <br/>
 * 
 * NOTE: To have more control on learning, you can add additional regularation
 * parameters to user/item biases. For simplicity, we do not do this.
 * 
 * @author guoguibing
 * 
 */
public class BiasedMF extends IterativeRecommender {

	public BiasedMF(SparseMat rm, SparseMat tm, int fold) {
		super(rm, tm, fold);

		algoName = "BiasedMF";
	}

	protected void initModel() {

		super.initModel();

		userBiases = new DenseVec(numUsers);
		itemBiases = new DenseVec(numItems);

		// initialize user bias
		userBiases.init(initMean, initStd);
		itemBiases.init(initMean, initStd);
	}

	@Override
	protected void buildModel() {

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
				double bu = userBiases.get(u);
				double sgd = euj - regU * bu;
				userBiases.add(u, lRate * sgd);

				loss += regU * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj - regI * bj;
				itemBiases.add(j, lRate * sgd);

				loss += regI * bj * bj;

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

	protected double predict(int u, int j) {
		return globalMean + userBiases.get(u) + itemBiases.get(j) + DenseMat.rowMult(P, u, Q, j);
	}

}
