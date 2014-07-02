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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.data;

import happy.coding.io.Strings;

/**
 * Data Structure, Lower Symmetric Matrix
 * 
 * @author guoguibing
 * 
 */
public class SymmMatrix {

	// matrix dimension
	protected int dim;
	// matrix data
	protected double[][] data;

	/**
	 * Construct a symmetric matrix
	 */
	public SymmMatrix(int dim) {
		this.dim = dim;

		data = new double[dim][];
		for (int i = 0; i < dim; i++)
			data[i] = new double[i + 1];
	}

	/**
	 * Construct a symmetric matrix by deeply copying data from a given matrix
	 */
	public SymmMatrix(SymmMatrix mat) {
		dim = mat.dim;

		data = new double[dim][];
		for (int i = 0; i < dim; i++) {
			data[i] = new double[i + 1];
			for (int j = 0; j < data[i].length; j++)
				data[i][j] = mat.data[i][j];
		}
	}

	/**
	 * Make a deep copy of current matrix
	 */
	public SymmMatrix clone() {
		return new SymmMatrix(this);
	}

	/**
	 * Get a value at entry (row, col)	
	 */
	public double get(int row, int col) {
		return row >= col ? data[row][col] : data[col][row];
	}

	/**
	 * set a value to entry (row, col) 
	 */
	public void set(int row, int col, double val) {
		if (row >= col)
			data[row][col] = val;
		else
			data[col][row] = val;
	}

	/**
	 * add a value to entry (row, col)	 
	 */
	public void add(int row, int col, double val) {
		if (row >= col)
			data[row][col] += val;
		else
			data[col][row] += val;
	}

	/**
	 * Retrieve a complete row of similar items	 
	 */
	public SparseVector row(int row) {
		SparseVector res = new SparseVector(dim);
		for (int col = 0; col < dim; col++) {
			double val = get(row, col);
			if (val != 0)
				res.set(col, val);
		}

		return res;
	}

	@Override
	public String toString() {
		return Strings.toString(data);
	}

}
