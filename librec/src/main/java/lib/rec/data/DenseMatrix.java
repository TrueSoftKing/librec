package lib.rec.data;

import happy.coding.math.Randoms;

import java.util.Arrays;

/**
 * Data Structure: dense matrix
 * 
 * A big reason that we do not adopt original DenseMatrix from M4J libraray is
 * because the latter using one-dimensional array to store data, which will
 * often cause OutOfMemory exception due to the over the maximum length of
 * one-dimensional Java array.
 * 
 * @author guoguibing
 * 
 */
public class DenseMatrix {

	protected int numRows, numCols;
	protected double[][] data;

	public DenseMatrix(int numRows, int numColumns) {
		this.numRows = numRows;
		this.numCols = numColumns;

		data = new double[numRows][numColumns];
	}

	public DenseMatrix(double[][] array) {
		this.numRows = array.length;
		this.numCols = array[0].length;

		data = Arrays.copyOf(array, array.length);
	}

	public DenseMatrix(DenseMatrix mat) {
		this.numRows = mat.numRows;
		this.numCols = mat.numCols;

		data = Arrays.copyOf(mat.data, mat.data.length);
	}

	public DenseMatrix clone() {
		return new DenseMatrix(this);
	}

	/**
	 * initialize a dense matrix with small Guassian values <br/>
	 * 
	 * <strong>NOTE:</strong> small initial values make it easier to train a
	 * model; otherwise a very small learning rate may be needed (especially
	 * when the number of factors is large) which can cause bad performance.
	 */
	public void init(double mean, double sigma) {
		for (int i = 0; i < numRows; i++)
			for (int j = 0; j < numCols; j++)
				data[i][j] = Randoms.gaussian(mean, sigma);
	}

	/**
	 * initialize a dense matrix with small random values
	 */
	public void init() {

		for (int i = 0; i < numRows; i++)
			for (int j = 0; j < numCols; j++)
				data[i][j] = Randoms.uniform(0.0, 0.01);
	}

	public double[][] getData() {
		return data;
	}

	public int numRows() {
		return numRows;
	}

	public int numCols() {
		return numCols;
	}

	/**
	 * row x row of two matrix
	 * 
	 * @param m
	 *            the first matrix
	 * @param mrow
	 *            row of the first matrix
	 * @param n
	 *            the second matrix
	 * @param nrow
	 *            row of the second matrix
	 * @return inner product of two row vectors
	 */
	public static double rowMult(DenseMatrix m, int mrow, DenseMatrix n, int nrow) {

		assert m.numCols == n.numCols;

		double result = 0;

		for (int j = 0, k = m.numCols; j < k; j++)
			result += m.get(mrow, j) * n.get(nrow, j);

		return result;
	}

	public double get(int row, int col) {
		return data[row][col];
	}

	public void set(int row, int col, double val) {
		data[row][col] = val;
	}

	public void add(int row, int col, double val) {
		data[row][col] += val;
	}

	public DenseMatrix scale(double val) {
		for (int i = 0; i < numRows; i++)
			for (int j = 0; j < numCols; j++)
				data[i][j] *= val;

		return this;
	}

	public DenseMatrix add(DenseMatrix mat) {

		assert numRows == mat.numRows;
		assert numCols == mat.numCols;

		for (int i = 0; i < numRows; i++)
			for (int j = 0; j < numCols; j++)
				data[i][j] += mat.get(i, j);

		return this;
	}

	/**
	 * set one value to the whole row
	 * 
	 * @param row
	 *            row id
	 * @param val
	 *            value to be set
	 */
	public void setRow(int row, double val) {
		Arrays.fill(data[row], val);
	}
}
