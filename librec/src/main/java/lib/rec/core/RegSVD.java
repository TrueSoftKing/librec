package lib.rec.core;

import happy.coding.io.Strings;
import lib.rec.MatrixUtils;
import lib.rec.intf.IterativeRecommender;
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
public class RegSVD extends IterativeRecommender {

	public RegSVD(CompRowMatrix rm, CompRowMatrix tm, int fold) {
		super(rm, tm, fold);

		algoName = "RegSVD";
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

	@Override
	public String toString() {
		double learnRate = cf.getDouble("val.learn.rate"); // re-get initial learn rate in case bold driver is used. 
		return Strings.toString(new Object[] { learnRate, regU, regI, numFactors, maxIters, isBoldDriver }, ",");
	}

}
