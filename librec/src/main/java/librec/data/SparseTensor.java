// Copyright (C) 2014-2015 Guibing Guo
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
package librec.data;

import happy.coding.io.Logs;
import happy.coding.math.Randoms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

/**
 * 
 * Data Structure: Sparse Tensor <br>
 * 
 * <p>
 * For easy documentation, here we use (keys, value) to indicate each entry of a tensor, where {@value keys} are the
 * keys of an entry.
 * </p>
 * 
 * <p>
 * <Strong>Reference:</strong> Kolda and Bader, <strong>Tensor Decompositions and Applications</strong>, SIAM REVIEW,
 * Vol. 51, No. 3, pp. 455–500
 * </p>
 * 
 * @author Guo Guibing
 *
 */
public class SparseTensor implements Iterable<TensorEntry>, Serializable {

	private static final long serialVersionUID = 2487513413901432943L;

	private class TensorIterator implements Iterator<TensorEntry> {

		private int index = 0;
		private SparseTensorEntry entry = new SparseTensorEntry();

		@Override
		public boolean hasNext() {
			return index < values.size();
		}

		@Override
		public TensorEntry next() {
			return entry.update(index++);
		}

		@Override
		public void remove() {
			entry.remove();
		}

	}

	private class SparseTensorEntry implements TensorEntry {

		private int index = -1;

		public SparseTensorEntry update(int index) {
			this.index = index;
			return this;
		}

		@Override
		public int key(int d) {
			return ndKeys[d].get(index);
		}

		@Override
		public double get() {
			return values.get(index);
		}

		@Override
		public void set(double value) {
			values.set(index, value);
		}

		/**
		 * remove the current entry
		 */
		public void remove() {
			for (int d = 0; d < numDimensions; d++) {

				// update indices if necessary
				if (isIndexed(d))
					keyIndices[d].remove(key(d), index);

				ndKeys[d].remove(index);
			}
			values.remove(index);
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int d = 0; d < numDimensions; d++) {
				sb.append(key(d)).append("\t");
			}
			sb.append(get());

			return sb.toString();
		}

		@Override
		public int[] keys() {
			int[] res = new int[numDimensions];
			for (int d = 0; d < numDimensions; d++) {
				res[d] = key(d);
			}

			return res;
		}

	}

	// TODO: re-factor variable names to be clear 
	
	/**
	 * number of dimensions, i.e., the order (or modes, ways) of a tensor
	 */
	private int numDimensions;
	private int[] dimensions;
	private List<Integer>[] ndKeys; // n-dimensional array
	private List<Double> values; // values

	private Multimap<Integer, Integer>[] keyIndices; // each multimap = {key, {pos1, pos2, ...}}
	private List<Integer> indexedKeys; // indexed dimensions

	// dimensions of users and items
	private int userDimension, itemDimension;

	/**
	 * Construct an empty sparse tensor
	 * 
	 * @param dims
	 *            dimensions of a tensor
	 */
	public SparseTensor(int... dims) {
		this(dims, null, null);
	}

	/**
	 * Construct a sparse tensor with indices and values
	 * 
	 * @param dims
	 *            dimensions of a tensor
	 * @param nds
	 *            n-dimensional indices
	 * @param vals
	 *            entry values
	 */
	@SuppressWarnings("unchecked")
	public SparseTensor(int[] dims, List<Integer>[] nds, List<Double> vals) {
		if (dims.length < 3)
			throw new Error("The dimension of a tensor cannot be smaller than 3!");

		numDimensions = dims.length;
		dimensions = new int[numDimensions];

		ndKeys = (List<Integer>[]) new List<?>[numDimensions];
		keyIndices = (Multimap<Integer, Integer>[]) new Multimap<?, ?>[numDimensions];

		for (int d = 0; d < numDimensions; d++) {
			dimensions[d] = dims[d];
			ndKeys[d] = nds == null ? new ArrayList<Integer>() : new ArrayList<Integer>(nds[d]);
			keyIndices[d] = HashMultimap.create();
		}

		values = vals == null ? new ArrayList<Double>() : new ArrayList<>(vals);
		indexedKeys = new ArrayList<>(numDimensions);
	}

	/**
	 * make a deep clone
	 */
	public SparseTensor clone() {
		SparseTensor res = new SparseTensor(dimensions);

		// copy indices and values
		for (int d = 0; d < numDimensions; d++) {
			res.ndKeys[d].addAll(this.ndKeys[d]);
			res.keyIndices[d].putAll(this.keyIndices[d]);
		}

		res.values.addAll(this.values);

		// copy indexed array
		res.indexedKeys.addAll(this.indexedKeys);

		return res;
	}

	/**
	 * Add a value to a given i-entry
	 * 
	 * @param val
	 *            value to add
	 * @param ndKeys
	 *            n-dimensional keys
	 */
	public void add(double val, int... ndKeys) {

		int index = findIndex(ndKeys);

		if (index >= 0) {
			// if i-entry exists: update value
			values.set(index, values.get(index) + val);
		} else {
			// if i-entry does not exist: add a new entry
			set(val, ndKeys);
		}
	}

	/**
	 * Set a value to a specific i-entry
	 * 
	 * @param val
	 *            value to set
	 * @param ndKeys
	 *            n-dimensional keys
	 */
	public void set(double val, int... keys) {
		int index = findIndex(keys);

		// if i-entry exists, set it a new value
		if (index >= 0) {
			values.set(index, val);
			return;
		}

		// otherwise insert a new entry
		for (int d = 0; d < numDimensions; d++) {
			ndKeys[d].add(keys[d]);

			// update indices if necessary
			if (isIndexed(d))
				keyIndices[d].put(keys[d], ndKeys[d].size() - 1);
		}
		values.add(val);

	}

	/**
	 * remove an entry with specific ndKeys
	 */
	public boolean remove(int... ndKeys) {
		return remove(findIndex(ndKeys));
	}

	/**
	 * remove an entry with a specific index
	 */
	private boolean remove(int index) {

		if (index < 0)
			return false;

		for (int d = 0; d < numDimensions; d++) {
			int key = ndKeys[d].get(index);
			ndKeys[d].remove(index);

			// update indices if necessary
			if (isIndexed(d))
				keyIndices[d].remove(key, index);
		}
		values.remove(index);

		return true;

	}

	/**
	 * @return all entries for a (user, item) pair
	 */
	public List<Integer> getIndices(int user, int item) {
		List<Integer> res = new ArrayList<>();

		Collection<Integer> userIndices = getIndex(userDimension, user);

		for (int index : userIndices) {
			if (ndKeys[itemDimension].get(index) == item) {
				res.add(index);
			}
		}

		return res;
	}

	/**
	 * find the inner index of a given i-entry
	 */
	private int findIndex(int... nd) {

		if (nd.length != numDimensions)
			throw new Error("The given input does not match with the tensor dimension!");

		// if no data exists
		if (values.size() == 0)
			return -1;

		// if no indexed dimension exists
		if (indexedKeys.size() == 0)
			buildIndex(0);

		// retrieve from the first indexed dimension
		int d = indexedKeys.get(0);

		// all relevant positions
		Collection<Integer> pos = keyIndices[d].get(nd[d]);
		if (pos == null || pos.size() == 0)
			return -1;

		// for each possible position
		for (int p : pos) {
			boolean found = true;
			for (int dd = 0; dd < numDimensions; dd++) {
				if (nd[dd] != ndKeys[dd].get(p)) {
					found = false;
					break;
				}
			}
			if (found)
				return p;
		}

		// if not found
		return -1;

	}

	/**
	 * A fiber is defined by fixing every index but one. For example, a matrix column is a mode-1 fiber and a matrix row
	 * is a mode-2 fiber.
	 * 
	 * @param dim
	 *            that dimension where values can vary
	 * @param nd
	 *            the other fixed dimension indices
	 * @return a sparse vector
	 */
	public SparseVector fiber(int dim, int... nd) {
		if ((nd.length != numDimensions - 1) || size() < 1)
			throw new Error("The input indices do not match the fiber specification!");

		// find an indexed dimension for searching indices
		int d = -1;
		if ((indexedKeys.size() == 0) || (indexedKeys.contains(dim) && indexedKeys.size() == 1)) {
			d = (dim != 0 ? 0 : 1);
			buildIndex(d);
		} else {
			for (int dd : indexedKeys) {
				if (dd != dim) {
					d = dd;
					break;
				}
			}
		}

		SparseVector res = new SparseVector(dimensions[dim]);

		// all relevant positions
		Collection<Integer> pos = keyIndices[d].get(nd[d < dim ? d : d - 1]);
		if (pos == null || pos.size() == 0)
			return res;

		// for each possible position
		for (int p : pos) {
			boolean found = true;
			for (int dd = 0, ndi = 0; dd < numDimensions; dd++) {

				if (dd == dim)
					continue;

				int key = ndKeys[dd].get(p);
				if (nd[ndi++] != key) {
					found = false;
					break;
				}
			}
			if (found) {
				res.set(ndKeys[dim].get(p), values.get(p));
			}
		}

		return res;
	}

	/**
	 * Check if a given i-entry exists in the tensor
	 * 
	 * @param nd
	 *            i-entry
	 * @return true if found, and false otherwise
	 */
	public boolean contains(int... nd) {
		return findIndex(nd) >= 0 ? true : false;
	}

	/**
	 * @return whether a dimension d is indexed
	 */
	public boolean isIndexed(int d) {
		return indexedKeys.contains(d);
	}

	/**
	 * @return whether a tensor is cubical
	 */
	public boolean isCubical() {
		int dim = dimensions[0];
		for (int d = 1; d < numDimensions; d++) {
			if (dim != dimensions[d])
				return false;
		}

		return true;
	}

	/**
	 * @return whether a tensor is diagonal
	 */
	public boolean isDiagonal() {
		for (TensorEntry te : this) {
			double val = te.get();
			if (val != 0) {
				int i = te.key(0);
				for (int d = 0; d < numDimensions; d++) {
					int j = te.key(d);
					if (i != j)
						return false;
				}
			}
		}

		return true;
	}

	/**
	 * @return a value given a specific i-entry
	 */
	public double get(int... nd) {
		assert nd.length == this.numDimensions;

		int index = findIndex(nd);
		return index < 0 ? 0 : values.get(index);
	}

	/**
	 * Shuffle a sparse tensor
	 */
	public void shuffle() {
		int len = size();
		for (int i = 0; i < len; i++) {
			// target index
			int j = i + Randoms.uniform(len - i);

			// swap values
			double tempVal = values.get(i);
			values.set(i, values.get(j));
			values.set(j, tempVal);

			// swap i-entries
			for (int d = 0; d < numDimensions; d++) {
				int ikey = ndKeys[d].get(i);
				int jkey = ndKeys[d].get(j);
				ndKeys[d].set(i, jkey);
				ndKeys[d].set(j, ikey);

				// update indices
				if (isIndexed(d)) {
					keyIndices[d].remove(jkey, j);
					keyIndices[d].put(jkey, i);

					keyIndices[d].remove(ikey, i);
					keyIndices[d].put(ikey, j);
				}

			}

		}
	}

	/**
	 * build index at dimensions nd
	 * 
	 * @param nd
	 *            dimensions to be indexed
	 */
	public void buildIndex(int... nd) {
		for (int d : nd) {
			keyIndices[d].clear();
			for (int index = 0; index < ndKeys[d].size(); index++) {
				int key = ndKeys[d].get(index);
				keyIndices[d].put(key, index);
			}

			indexedKeys.add(d);
		}
	}

	/**
	 * build index for all dimensions
	 */
	public void buildIndices() {
		for (int d = 0; d < numDimensions; d++) {
			buildIndex(d);
		}
	}

	/**
	 * @return indices (positions) of a key in dimension d
	 */
	public Collection<Integer> getIndex(int d, int key) {
		Multimap<Integer, Integer> indices = keyIndices[d];
		if (indices == null || indices.size() == 0)
			buildIndex(d);

		return indices.get(key);
	}

	/**
	 * @return keys in a given index
	 */
	public int[] keys(int index) {
		int[] res = new int[numDimensions];
		for (int d = 0; d < numDimensions; d++) {
			res[d] = ndKeys[d].get(index);
		}

		return res;
	}
	
	/**
	 * @return value in a given index
	 */
	public double value(int index){
		return values.get(index);
	}

	/**
	 * @param sd
	 *            source dimension
	 * @param key
	 *            key in the source dimension
	 * @param td
	 *            target dimension
	 * 
	 * @return indices in a target dimension {@code td} related with a key in dimension {@code sd}
	 */
	public List<Integer> getRelevantIndex(int sd, int key, int td) {
		Collection<Integer> indices = getIndex(sd, key);
		List<Integer> res = null;
		if (indices != null) {
			res = new ArrayList<>();
			for (int index : indices) {
				res.add(ndKeys[td].get(index));
			}
		}

		return res;
	}

	/**
	 * @return number of entries of the tensor
	 */
	public int size() {
		return values.size();
	}

	/**
	 * Slice is a two-dimensional section of a tensor, defined by fixing all but two indices.
	 * 
	 * @param rowDim
	 *            row dimension
	 * @param colDim
	 *            column dimension
	 * @param nd
	 *            indices of other dimensions
	 * 
	 * @return a sparse matrix
	 */
	public SparseMatrix slice(int rowDim, int colDim, int... nd) {

		if (nd.length != numDimensions - 2)
			throw new Error("The input dimensions do not match the tensor specification!");

		// find an indexed array to search 
		int d = -1;
		boolean cond1 = indexedKeys.size() == 0;
		boolean cond2 = (indexedKeys.contains(rowDim) || indexedKeys.contains(colDim)) && indexedKeys.size() == 1;
		boolean cond3 = indexedKeys.contains(rowDim) && indexedKeys.contains(colDim) && indexedKeys.size() == 2;
		if (cond1 || cond2 || cond3) {
			for (d = 0; d < numDimensions; d++) {
				if (d != rowDim && d != colDim)
					break;
			}
			buildIndex(d);
		} else {
			for (int dd : indexedKeys) {
				if (dd != rowDim && dd != colDim) {
					d = dd;
					break;
				}
			}
		}

		// get search key
		int key = -1;
		for (int dim = 0, i = 0; dim < numDimensions; dim++) {
			if (dim == rowDim || dim == colDim)
				continue;

			if (dim == d) {
				key = nd[i];
				break;
			}
			i++;
		}

		// all relevant positions
		Collection<Integer> pos = keyIndices[d].get(key);
		if (pos == null || pos.size() == 0)
			return null;

		Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
		Multimap<Integer, Integer> colMap = HashMultimap.create();

		// for each possible position
		for (int p : pos) {
			boolean found = true;
			for (int dd = 0, j = 0; dd < numDimensions; dd++) {

				if (dd == rowDim || dd == colDim)
					continue;

				int key2 = ndKeys[dd].get(p);
				if (nd[j++] != key2) {
					found = false;
					break;
				}
			}
			if (found) {
				int row = ndKeys[rowDim].get(p);
				int col = ndKeys[colDim].get(p);
				double val = values.get(p);

				dataTable.put(row, col, val);
				colMap.put(col, row);
			}
		}

		return new SparseMatrix(dimensions[rowDim], dimensions[colDim], dataTable, colMap);
	}

	/**
	 * Transforming a tensor into a matrix
	 * 
	 * @param n
	 *            mode or dimension
	 * @return an unfolded or flatten matrix
	 */
	public SparseMatrix unfoldings(int n) {
		int numRows = dimensions[n];
		int numCols = 1;
		for (int d = 0; d < numDimensions; d++) {
			if (d != n)
				numCols *= dimensions[d];
		}

		Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
		Multimap<Integer, Integer> colMap = HashMultimap.create();
		for (TensorEntry te : this) {
			int[] indices = te.keys();

			int i = indices[n];
			int j = 0;
			for (int k = 0; k < numDimensions; k++) {
				if (k == n)
					continue;

				int ik = indices[k];
				int jk = 1;
				for (int m = 0; m < k; m++) {
					if (m == n)
						continue;
					jk *= dimensions[m];
				}

				j += ik * jk;
			}

			dataTable.put(i, j, te.get());
			colMap.put(j, i);
		}

		return new SparseMatrix(numRows, numCols, dataTable, colMap);
	}

	/**
	 * retrieve a rating matrix from the tensor. Warning: it assumes there is at most one entry for each (user, item)
	 * pair.
	 * 
	 * @param userDim
	 *            dimension of users
	 * @param itemDim
	 *            dimension of items
	 * @return a sparse rating matrix
	 */
	public SparseMatrix rateMatrix() {

		Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
		Multimap<Integer, Integer> colMap = HashMultimap.create();

		for (TensorEntry te : this) {
			int u = te.key(userDimension);
			int i = te.key(itemDimension);

			dataTable.put(u, i, te.get());
			colMap.put(i, u);
		}

		return new SparseMatrix(dimensions[userDimension], dimensions[itemDimension], dataTable, colMap);
	}

	@Override
	public Iterator<TensorEntry> iterator() {
		return new TensorIterator();
	}

	/**
	 * @return norm of a tensor
	 */
	public double norm() {
		double res = 0;

		for (double val : values) {
			res += val * val;
		}

		return Math.sqrt(res);
	}

	/**
	 * @return inner product with another tensor
	 */
	public double inner(SparseTensor st) throws Exception {
		if (!isDimMatch(st))
			throw new Exception("The dimensions of two sparse tensors do not match!");

		double res = 0;
		for (TensorEntry te : this) {
			double v1 = te.get();
			double v2 = st.get(te.keys());

			res += v1 * v2;
		}

		return res;
	}

	/**
	 * @return whether two sparse tensors have the same dimensions
	 */
	public boolean isDimMatch(SparseTensor st) {
		if (numDimensions != st.numDimensions)
			return false;

		boolean match = true;
		for (int d = 0; d < numDimensions; d++) {
			if (dimensions[d] != st.dimensions[d]) {
				match = false;
				break;
			}
		}

		return match;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("N-Dimension: ").append(numDimensions).append(", Size: ").append(size()).append("\n");
		for (int i = 0; i < values.size(); i++) {
			for (int d = 0; d < numDimensions; d++) {
				sb.append(ndKeys[d].get(i)).append("\t");
			}
			sb.append(values.get(i)).append("\n");
		}

		return sb.toString();
	}

	/**
	 * Usage demonstration
	 */
	public static void main(String[] args) throws Exception {
		SparseTensor st = new SparseTensor(4, 4, 6);
		st.set(1.0, 1, 0, 0);
		st.set(1.5, 1, 0, 0); // overwrite value
		st.set(2.0, 1, 1, 0);
		st.set(3.0, 2, 0, 0);
		st.set(4.0, 1, 3, 0);
		st.set(5.0, 1, 0, 5);
		st.set(6.0, 3, 1, 4);

		Logs.debug(st);
		Logs.debug("I-Entry (1, 0, 0) = {}", st.get(1, 0, 0));
		Logs.debug("I-Entry (1, 1, 0) = {}", st.get(1, 1, 0));
		Logs.debug("I-Entry (1, 2, 0) = {}", st.get(1, 2, 0));
		Logs.debug("I-Entry (2, 0, 0) = {}", st.get(2, 0, 0));
		Logs.debug("I-Entry (1, 0, 6) = {}", st.get(1, 0, 6));
		Logs.debug("I-Entry (3, 1, 4) = {}", st.get(3, 1, 4));

		Logs.debug("dimension 0 key 1 = {}", st.getIndex(0, 1));
		Logs.debug("dimension 1 key 3 = {}", st.getIndex(1, 3));
		Logs.debug("dimension 2 key 1 = {}", st.getIndex(2, 1));
		Logs.debug("dimension 2 key 6 = {}", st.getIndex(2, 6));

		st.set(4.5, 2, 1, 1);
		Logs.debug(st);
		Logs.debug("dimension 2 key 1 = {}", st.getIndex(2, 1));
		st.remove(2, 1, 1);
		Logs.debug("dimension 2 key 1 = {}", st.getIndex(2, 1));

		Logs.debug("index of i-entry (1, 2, 0) = {}, value = {}", st.findIndex(1, 2, 0), st.get(1, 2, 0));
		Logs.debug("index of i-entry (3, 1, 4) = {}, value = {}", st.findIndex(3, 1, 4), st.get(3, 1, 4));

		Logs.debug("indices in dimension 2 associated with dimension 0 key 1 = {}", st.getRelevantIndex(0, 1, 2));

		// norm
		Logs.debug("norm = {}", st.norm());

		// clone
		SparseTensor st2 = st.clone();
		Logs.debug("make a clone = {}", st2);

		// inner product
		Logs.debug("inner with the clone = {}", st.inner(st2));

		// fiber
		Logs.debug("fiber (0, 0, 0) = {}", st.fiber(0, 0, 0));
		Logs.debug("fiber (1, 1, 0) = {}", st.fiber(1, 1, 0));
		Logs.debug("fiber (2, 1, 0) = {}", st.fiber(2, 1, 0));

		// slice
		Logs.debug("slice (0, 1, 0) = {}", st.slice(0, 1, 0));
		Logs.debug("slice (0, 2, 1) = {}", st.slice(0, 2, 1));
		Logs.debug("slice (1, 2, 1) = {}", st.slice(1, 2, 1));

		// iterator
		for (TensorEntry te : st) {
			te.set(te.get() + 0.588);
		}
		Logs.debug("Before shuffle: {}", st);

		// shuffle
		st.shuffle();
		Logs.debug("After shuffle: {}", st);

		// new tensor: example given by the reference paper in (2.1)
		st = new SparseTensor(3, 4, 2);

		st.set(1, 0, 0, 0);
		st.set(4, 0, 1, 0);
		st.set(7, 0, 2, 0);
		st.set(10, 0, 3, 0);
		st.set(2, 1, 0, 0);
		st.set(5, 1, 1, 0);
		st.set(8, 1, 2, 0);
		st.set(11, 1, 3, 0);
		st.set(3, 2, 0, 0);
		st.set(6, 2, 1, 0);
		st.set(9, 2, 2, 0);
		st.set(12, 2, 3, 0);

		st.set(13, 0, 0, 1);
		st.set(16, 0, 1, 1);
		st.set(19, 0, 2, 1);
		st.set(22, 0, 3, 1);
		st.set(14, 1, 0, 1);
		st.set(17, 1, 1, 1);
		st.set(20, 1, 2, 1);
		st.set(23, 1, 3, 1);
		st.set(15, 2, 0, 1);
		st.set(18, 2, 1, 1);
		st.set(21, 2, 2, 1);
		st.set(24, 2, 3, 1);

		Logs.debug("A new tensor = {}", st);
		Logs.debug("Mode X0 unfoldings = {}", st.unfoldings(0));
		Logs.debug("Mode X1 unfoldings = {}", st.unfoldings(1));
		Logs.debug("Mode X2 unfoldings = {}", st.unfoldings(2));
	}

	public int getUserDimension() {
		return userDimension;
	}

	public void setUserDimension(int userDimension) {
		this.userDimension = userDimension;
	}

	public int getItemDimension() {
		return itemDimension;
	}

	public void setItemDimension(int itemDimension) {
		this.itemDimension = itemDimension;
	}

	public int[] dimensions() {
		return dimensions;
	}

	public int numDimensions() {
		return numDimensions;
	}

}
