// Copyright (C) 2014-2005 Guibing Guo
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
package librec.rating;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Randoms;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.MatrixEntry;
import librec.data.RatingContext;
import librec.data.SparseMatrix;
import librec.intf.ContextRecommender;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Koren, <strong>Collaborative Filtering with Temporal Dynamics</strong>, KDD 2009.
 * 
 * @author guoguibing
 * 
 */
public class TimeSVDPlusPlus extends ContextRecommender {

	// the span of days of rating timestamps
	private static int numDays;

	// {user, mean date}
	private DenseVector userMeanDate;

	// minimum/maximum rating timestamp
	private static long minTimestamp, maxTimestamp;

	// time decay factor
	private float beta;

	// number of bins over all the items
	private int numBins;

	// item's implicit influence
	private DenseMatrix Y;

	// {item, bin(t)} bias matrix
	private DenseMatrix Bit;

	// {user, day, bias} table
	private Table<Integer, Integer, Double> But;

	// user bias weight parameters
	private DenseVector Alpha;

	// {user, feature} alpha matrix
	private DenseMatrix Auk;

	// {user, {feature, day, value} } map
	private Map<Integer, Table<Integer, Integer, Double>> Pukt;

	// {user, user scaling stable part}
	private DenseVector Cu;

	// {user, day, day-specific scaling part}
	private DenseMatrix Cut;

	// time unit may depend on data sets, e.g. in MovieLens, it is unix seconds
	private final static TimeUnit secs = TimeUnit.SECONDS;

	// read context information
	static {
		try {
			readContext();

		} catch (Exception e) {
			Logs.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public TimeSVDPlusPlus(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "timeSVD++";

		beta = cf.getFloat("timeSVD++.beta");
		numBins = cf.getInt("timeSVD++.item.bins");
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();

		userBias = new DenseVector(numUsers);
		userBias.init();

		itemBias = new DenseVector(numItems);
		itemBias.init();

		Alpha = new DenseVector(numUsers);
		Alpha.init();

		Bit = new DenseMatrix(numItems, numBins);
		Bit.init();

		Y = new DenseMatrix(numItems, numFactors);
		Y.init();

		Auk = new DenseMatrix(numUsers, numFactors);
		Auk.init();

		But = HashBasedTable.create();
		Pukt = new HashMap<>();

		Cu = new DenseVector(numUsers);
		Cu.init();

		Cut = new DenseMatrix(numUsers, numDays);
		Cut.init();

		// cache
		userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);

		// global average date
		double sum = 0;
		int cnt = 0;
		for (MatrixEntry me : trainMatrix) {
			int u = me.row();
			int i = me.column();
			double rui = me.get();

			if (rui <= 0)
				continue;

			sum += days(ratingContexts.get(u, i).getTimestamp(), minTimestamp);
			cnt++;
		}
		double globalMeanDate = sum / cnt;

		// compute user's mean of rating timestamps
		userMeanDate = new DenseVector(numUsers);
		List<Integer> Ru = null;
		for (int u = 0; u < numUsers; u++) {

			sum = 0;
			Ru = userItemsCache.get(u);
			for (int i : Ru) {
				sum += days(ratingContexts.get(u, i).getTimestamp(), minTimestamp);
			}

			double mean = (Ru.size() > 0) ? (sum + 0.0) / Ru.size() : globalMeanDate;
			userMeanDate.set(u, mean);
		}
	}

	@Override
	protected void buildModel() throws Exception {
		for (int iter = 1; iter <= numIters; iter++) {
			errs = 0;
			loss = 0;

			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int i = me.column();
				double rui = me.get();
				if (rui <= 0)
					continue;

				long timestamp = ratingContexts.get(u, i).getTimestamp();
				// day t
				int t = days(timestamp, minTimestamp);
				int bin = bin(t);
				double dev_ut = dev(u, t);

				double bi = itemBias.get(i);
				double bit = Bit.get(i, bin);
				double bu = userBias.get(u);

				double cu = Cu.get(u);
				double cut = Cut.get(u, t);

				// lazy initialization
				if (!But.contains(u, t))
					But.put(u, t, Randoms.random());
				double but = But.get(u, t);

				double au = Alpha.get(u); // alpha_u

				double pui = globalMean + (bi + bit) * (cu + cut); // mu + bi(t)
				pui += bu + au * dev_ut + but; // bu(t)

				// qi * yj
				List<Integer> Ru = userItemsCache.get(u);
				double sum_y = 0;
				for (int j : Ru) {
					sum_y += DenseMatrix.rowMult(Y, j, Q, i);
				}
				double wi = Ru.size() > 0 ? Math.pow(Ru.size(), -0.5) : 0;
				pui += sum_y * wi;

				// qi * pu(t)
				if (!Pukt.containsKey(u)) {
					Table<Integer, Integer, Double> data = HashBasedTable.create();
					Pukt.put(u, data);
				}

				Table<Integer, Integer, Double> Pkt = Pukt.get(u);
				for (int k = 0; k < numFactors; k++) {
					double qik = Q.get(i, k);

					// lazy initialization
					if (!Pkt.contains(k, t))
						Pkt.put(k, t, Randoms.random());

					double puk = P.get(u, k) + Auk.get(u, k) * dev_ut + Pkt.get(k, t);

					pui += puk * qik;
				}

				double eui = pui - rui;
				errs += eui * eui;
				loss += eui * eui;

				// update bi
				double sgd = eui * (cu + cut) + regB * bi;
				itemBias.add(i, -lRate * sgd);
				loss += regB * bi * bi;

				// update bi,bin(t)
				sgd = eui * (cu + cut) + regB * bit;
				Bit.add(i, bin, -lRate * sgd);
				loss += regB * bit * bit;

				// update bu
				sgd = eui + regB * bu;
				userBias.add(u, -lRate * sgd);
				loss += regB * bu * bu;

				// update au
				sgd = eui * dev_ut + regB * au;
				Alpha.add(u, -lRate * sgd);
				loss += regB * au * au;

				// update but
				sgd = eui + regB * but;
				double delta = but - lRate * sgd;
				But.put(u, t, delta);
				loss += regB * but * but;

				// update cu
				sgd = eui * (bi + bit) + regB * cu;
				Cu.add(u, -lRate * sgd);
				loss += regB * cu * cu;

				// update cut
				sgd = eui * (bi + bit) + regB * cut;
				Cut.add(u, t, -lRate * sgd);
				loss += regB * cut * cut;

				for (int k = 0; k < numFactors; k++) {
					double qik = Q.get(i, k);
					double puk = P.get(u, k);
					double auk = Auk.get(u, k);
					double pkt = Pkt.get(k, t);

					// update qik
					double pukt = puk + auk * dev_ut + pkt;

					double sum_yk = 0;
					for (int j : Ru) {
						sum_yk += Y.get(j, k);
					}

					sgd = eui * (pukt + wi * sum_yk) + regI * qik;
					Q.add(i, k, -lRate * sgd);
					loss += regI * qik * qik;

					// update puk
					sgd = eui * qik + regU * puk;
					P.add(u, k, -lRate * sgd);
					loss += regU * puk * puk;

					// update auk
					sgd = eui * qik * dev_ut + regU * auk;
					Auk.add(u, k, -lRate * sgd);
					loss += regU * auk * auk;

					// update pkt
					sgd = eui * qik + regU * pkt;
					delta = pkt - lRate * sgd;
					Pkt.put(k, t, delta);
					loss += regU * pkt * pkt;

					// update yjk
					for (int j : Ru) {
						double yjk = Y.get(j, k);
						sgd = eui * wi * qik + regI * yjk;
						Y.add(j, k, -lRate * sgd);
						loss += regI * yjk * yjk;
					}
				}
			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int i) throws Exception {
		// retrieve the test rating timestamp
		long timestamp = ratingContexts.get(u, i).getTimestamp();
		int t = days(timestamp, minTimestamp);
		int bin = bin(t);
		double dev_ut = dev(u, t);

		double pred = globalMean;

		// bi(t): eq. (12)
		pred += (itemBias.get(i) + Bit.get(i, bin)) * (Cu.get(u) + Cut.get(u, t));

		// bu(t): eq. (9)
		double but = But.contains(u, t) ? But.get(u, t) : 0;
		pred += userBias.get(u) + Alpha.get(u) * dev_ut + but;

		// qi * yj
		List<Integer> Ru = userItemsCache.get(u);
		double sum_y = 0;
		for (int j : Ru)
			sum_y += DenseMatrix.rowMult(Y, j, Q, i);

		double wi = Ru.size() > 0 ? Math.pow(Ru.size(), -0.5) : 0;
		pred += sum_y * wi;

		// qi * pu(t)
		for (int k = 0; k < numFactors; k++) {
			double qik = Q.get(i, k);
			// eq. (13)
			double puk = P.get(u, k) + Auk.get(u, k) * dev_ut;

			if (Pukt.containsKey(u)) {
				Table<Integer, Integer, Double> pkt = Pukt.get(u);
				if (pkt != null) {
					// eq. (13)
					puk += (pkt.contains(k, t) ? pkt.get(k, t) : 0);
				}
			}

			pred += puk * qik;
		}

		return pred;
	}

	@Override
	public String toString() {
		return super.toString() + "," + Strings.toString(new Object[] { beta, numBins });
	}

	/**
	 * Read rating timestamps
	 */
	protected static void readContext() throws Exception {
		String contextPath = cf.getPath("dataset.social");
		Logs.debug("Context dataset: {}", Strings.last(contextPath, 38));

		ratingContexts = HashBasedTable.create();
		BufferedReader br = FileIO.getReader(contextPath);
		String line = null;
		RatingContext rc = null;

		minTimestamp = Long.MAX_VALUE;
		maxTimestamp = Long.MIN_VALUE;

		while ((line = br.readLine()) != null) {
			String[] data = line.split("[ \t,]");
			String user = data[0];
			String item = data[1];

			// convert to million seconds
			long timestamp = secs.toMillis(Long.parseLong(data[3]));

			int userId = rateDao.getUserId(user);
			int itemId = rateDao.getItemId(item);

			rc = new RatingContext(userId, itemId);
			rc.setTimestamp(timestamp);

			ratingContexts.put(userId, itemId, rc);

			if (minTimestamp > timestamp)
				minTimestamp = timestamp;
			if (maxTimestamp < timestamp)
				maxTimestamp = timestamp;
		}

		numDays = days(maxTimestamp, minTimestamp) + 1;
	}

	/***************************************************************** Functional Methods *******************************************/
	/**
	 * @return the time deviation for a specific timestamp t w.r.t the mean date tu
	 */
	protected double dev(int u, int t) {
		double tu = userMeanDate.get(u);

		// date difference in days
		double diff = t - tu;

		return Math.signum(diff) * Math.pow(Math.abs(diff), beta);
	}

	/**
	 * @return the bin number (starting from 0..numBins-1) for a specific timestamp t;
	 */
	protected int bin(int day) {
		return (int) (day / (numDays + 0.0) * numBins);
	}

	/**
	 * @return number of days for a given time difference
	 */
	protected static int days(long diff) {
		return (int) TimeUnit.MILLISECONDS.toDays(diff);
	}

	/**
	 * @return number of days between two timestamps
	 */
	protected static int days(long t1, long t2) {
		return days(Math.abs(t1 - t2));
	}
}
