package librec.rating;

import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Gaussian;
import happy.coding.math.Randoms;
import happy.coding.math.Stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.data.Configuration;
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.GraphicRecommender;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Thomas Hofmann, <stron>Collaborative Filtering via Gaussian Probabilistic Latent Semantic Analysis</strong>, SIGIR
 * 2003.
 * 
 * @author Guo Guibing
 *
 */
@Configuration("factors, iters, burn.in, sample.lag, q")
public class GPLSA extends GraphicRecommender {

	// {user, item, {factor z, probability}}
	private Table<Integer, Integer, Map<Integer, Double>> Q;
	private DenseMatrix Mu, Sigma;

	private DenseVector mu, sigma;
	private float q; // smoothing weight 

	private double preRMSE;

	public GPLSA(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);
	}

	@Override
	protected void initModel() throws Exception {
		Mu = new DenseMatrix(numItems, numFactors);
		Mu.init();

		Sigma = new DenseMatrix(numItems, numFactors);
		Sigma.init();

		// Pz_u
		theta = new DenseMatrix(numUsers, numFactors);
		for (int u = 0; u < numUsers; u++) {
			double[] probs = Randoms.randProbs(numFactors);
			for (int k = 0; k < numFactors; k++) {
				theta.set(u, k, probs[k]);
			}
		}

		// normalize ratings
		double mean = globalMean;
		double sd = Stats.sd(trainMatrix.getData(), mean);

		q = algoOptions.getFloat("-q");
		mu = new DenseVector(numUsers);
		sigma = new DenseVector(numUsers);
		for (int u = 0; u < numUsers; u++) {
			SparseVector ru = trainMatrix.row(u);
			int Nu = ru.size();
			if (Nu < 1)
				continue;

			// compute mu_u
			double mu_u = (ru.sum() + q * mean) / (Nu + q);
			mu.set(u, mu_u);

			// compute sigma_u
			double sum = 0;
			for (VectorEntry ve : ru) {
				sum += Math.pow(ve.get() - mu_u, 2);
			}
			sum += q * Math.pow(sd, 2);
			double sigma_u = Math.sqrt(sum / (Nu + q));
			sigma.set(u, sigma_u);
		}

		// initialize Q
		Q = HashBasedTable.create();

		for (MatrixEntry me : trainMatrix) {
			int u = me.row();
			int i = me.column();
			double rate = me.get();

			double r = (rate - mu.get(u)) / sigma.get(u); // continuous ratings
			me.set(r);

			Q.put(u, i, new HashMap<Integer, Double>());
		}
	}

	@Override
	protected void eStep() {
		// variational inference to compute Q
		int count = 0, total = trainMatrix.size();
		for (MatrixEntry me : trainMatrix) {
			int u = me.row();
			int i = me.column();
			double r = me.get();

			count++;
			if (count % 100 == 0)
				Logs.debug("Count = {}/{}", count, total);

			double denominator = 0;
			double[] numerator = new double[numFactors];
			for (int z = 0; z < numFactors; z++) {
				double pdf = Gaussian.pdf(r, Mu.get(i, z), Sigma.get(i, z));
				double val = theta.get(u, z) * pdf;

				if (Double.isNaN(pdf))
					System.out.println(pdf);

				numerator[z] = val;
				denominator += val;
			}

			if (denominator == 0 || Double.isNaN(denominator))
				System.out.println();

			Map<Integer, Double> factorProbs = Q.get(u, i);
			for (int z = 0; z < numFactors; z++) {
				factorProbs.put(z, numerator[z] / denominator);
			}
		}
	}

	@Override
	protected void mStep() {

		// theta_u,z
		for (int u = 0; u < numUsers; u++) {
			List<Integer> items = trainMatrix.getColumns(u);
			if (items.size() < 1)
				continue;

			double[] numerator = new double[numFactors];
			double denominator = 0;
			for (int z = 0; z < numFactors; z++) {

				for (int i : items) {
					numerator[z] += Q.get(u, i).get(z);
				}

				denominator += numerator[z];
			}

			for (int z = 0; z < numFactors; z++) {
				theta.set(u, z, numerator[z] / denominator);
			}
		}

		// Mu, Sigma
		for (int i = 0; i < numItems; i++) {
			List<Integer> users = trainMatrix.getRows(i);
			if (users.size() < 1)
				continue;

			for (int z = 0; z < numFactors; z++) {
				double numerator = 0, denominator = 0;

				for (int u : users) {
					double r = trainMatrix.get(u, i);
					double prob = Q.get(u, i).get(z);

					numerator += r * prob;
					denominator += prob;
				}

				double mu = denominator > 0 ? numerator / denominator : 0;
				Mu.set(i, z, mu);

				numerator = 0;
				denominator = 0;
				for (int u : users) {
					double r = trainMatrix.get(u, i);
					double prob = Q.get(u, i).get(z);

					numerator += Math.pow(r - mu, 2) * prob;
					denominator += prob;
				}
				double sigma = denominator > 0 ? Math.sqrt(numerator / denominator) : 0;
				Sigma.set(i, z, sigma);
			}

		}

	}

	@Override
	protected double predict(int u, int i) throws Exception {

		double sum = 0;
		for (int z = 0; z < numFactors; z++) {
			sum += theta.get(u, z) * Mu.get(i, z);
		}

		return mu.get(u) + sigma.get(u) * sum;
	}

	@Override
	protected boolean isConverged(int iter) throws Exception {

		if (validationMatrix == null)
			return false;

		// compute current RMSE
		int numCount = 0;
		double sum = 0;
		for (MatrixEntry me : validationMatrix) {
			double rate = me.get();

			int u = me.row();
			int j = me.column();

			double pred = predict(u, j, true);
			if (Double.isNaN(pred))
				continue;

			double err = rate - pred;

			sum += err * err;
			numCount++;
		}

		double RMSE = Math.sqrt(sum / numCount);
		double delta = RMSE - preRMSE;

		if (verbose) {
			Logs.debug("{}{} iter {} achieves RMSE = {}, delta_RMSE = {}", algoName, foldInfo, iter, (float) RMSE,
					(float) (delta));
		}

		if (delta > 0)
			return true;

		preRMSE = RMSE;
		return false;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, numIters, burnIn, sampleLag, q }, ", ");
	}
}
