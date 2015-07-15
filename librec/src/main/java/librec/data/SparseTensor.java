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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * 
 * Data Structure: Sparse Tensor
 * 
 * @author Guo Guibing
 *
 */
public class SparseTensor {

	private int nd;
	private List<Integer>[] ndArray; // n-dimensional array
	private List<Double> values; // values

	private Multimap<Integer, Integer>[] ndIndices; // each multimap = {key, {pos1, pos2, ...}}

	/**
	 * Construct an empty sparse tensor
	 * 
	 * @param nd
	 *            number of dimensions of the tensor
	 */
	@SuppressWarnings("unchecked")
	public SparseTensor(int nd) {

		this.nd = nd;
		ndArray = (List<Integer>[]) new List<?>[nd];
		ndIndices = (Multimap<Integer, Integer>[]) new Multimap<?, ?>[nd];

		for (int d = 0; d < nd; d++) {
			ndArray[d] = new ArrayList<Integer>();
			ndIndices[d] = HashMultimap.create();
		}

		values = new ArrayList<>();
	}

	/**
	 * Add a value to specific indices
	 * 
	 * @param val
	 *            value to add
	 * @param nd
	 *            specific indices
	 */
	public void add(double val, int... nd) {
		assert nd.length == this.nd;

		for (int d = 0; d < nd.length; d++) {
			ndArray[d].add(nd[d]);
		}
		values.add(val);
	}

	/**
	 * find the inner index of a given array entry
	 */
	public int findIndex(int... nd) {
		// first, retrieve from indexed dimension
		for (int d = 0; d < nd.length; d++) {
			Multimap<Integer, Integer> indices = ndIndices[d];
			// if indexed
			if (indices.size() > 0) {
				int key = nd[d];
				// all relevant positions
				Collection<Integer> pos = indices.get(key);
				if (pos == null)
					return -1;
				// for each possible position
				for (int p : pos) {
					boolean found = true;
					for (int dd = 0; dd < nd.length; dd++) {
						if (nd[dd] != ndArray[dd].get(p)) {
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
		}

		// otherwise, let's index the first dimension and invoke this method again
		buildIndex(0);

		return findIndex(nd);
	}

	/**
	 * @return a value given the dimension values nd
	 */
	public double get(int... nd) {
		assert nd.length == this.nd;

		int index = findIndex(nd);
		return index < 0 ? 0 : values.get(index);
	}

	/**
	 * build index at dimension d
	 * 
	 * @param d
	 *            dimension to be indexed
	 */
	public void buildIndex(int d) {
		for (int index = 0; index < ndArray[d].size(); index++) {
			int key = ndArray[d].get(index);
			ndIndices[d].put(key, index);
		}
	}

	/**
	 * build index for all dimensions
	 */
	public void buildIndices() {
		for (int d = 0; d < ndArray.length; d++) {
			buildIndex(d);
		}
	}

	/**
	 * @return indices (positions) of a key in dimension d
	 */
	public Collection<Integer> getIndex(int d, int key) {
		Multimap<Integer, Integer> indices = ndIndices[d];
		if (indices == null || indices.size() == 0)
			buildIndex(d);

		return indices.get(key);
	}

	/**
	 * @return indices in a target dimension td related with a key in dimension d
	 */
	public List<Integer> getIndex(int sd, int key, int td) {
		Collection<Integer> indices = getIndex(sd, key);
		List<Integer> res = null;
		if (indices != null) {
			res = new ArrayList<>();
			for (int index : indices) {
				res.add(ndArray[td].get(index));
			}
		}

		return res;
	}

	/**
	 * @return size of the tensor
	 */
	public int size() {
		return values.size();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("N-Dimension: ").append(nd).append(", Size: ").append(size()).append("\n");
		for (int i = 0; i < values.size(); i++) {
			for (int d = 0; d < nd; d++) {
				sb.append(ndArray[d].get(i)).append("\t");
			}
			sb.append(values.get(i)).append("\n");
		}

		return sb.toString();
	}

	/**
	 * Usage demonstration
	 */
	public static void main(String[] args) {
		SparseTensor st = new SparseTensor(3);
		st.add(1.0, 1, 0, 0);
		st.add(2.0, 1, 1, 0);
		st.add(3.0, 2, 0, 0);
		st.add(4.0, 1, 3, 0);
		st.add(5.0, 1, 0, 6);
		st.add(6.0, 3, 1, 4);

		Logs.debug(st);
		Logs.debug("Entry (1, 0, 0) = {}", st.get(1, 0, 0));
		Logs.debug("Entry (1, 1, 0) = {}", st.get(1, 1, 0));
		Logs.debug("Entry (1, 2, 0) = {}", st.get(1, 2, 0));
		Logs.debug("Entry (2, 0, 0) = {}", st.get(2, 0, 0));
		Logs.debug("Entry (1, 0, 6) = {}", st.get(1, 0, 6));
		Logs.debug("Entry (3, 1, 4) = {}", st.get(3, 1, 4));

		Logs.debug("dimension 0 key 1 = {}", st.getIndex(0, 1));
		Logs.debug("dimension 1 key 3 = {}", st.getIndex(1, 3));
		Logs.debug("dimension 2 key 1 = {}", st.getIndex(2, 1));
		Logs.debug("dimension 2 key 6 = {}", st.getIndex(2, 6));
		
		Logs.debug("index of entry (1, 2, 0) = {}, value = {}", st.findIndex(1, 2, 0), st.get(1, 2, 0));
		Logs.debug("index of entry (3, 1, 4) = {}, value = {}", st.findIndex(3, 1, 4), st.get(3, 1, 4));

		Logs.debug("indices in dimension 2 associated with dimension 0 key 1 = {}", st.getIndex(0, 1, 2));
	}

}
