package otocloud.servermanager.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;

@SuppressWarnings("unchecked")
public class RingQueue<T> implements Iterable<T> {

	private int capacity = 128;
	private Object[] elementData;
	int head = -1;
	int tail = -1;

	public RingQueue(int capacity) {
		this.capacity = capacity;
		elementData = new Object[capacity];
	}

	public int size() {
		return head == -1 ? 0 : head - tail + 1;
	}

	public boolean isEmpty() {
		return head == -1;
	}

	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {

			private int index = tail;

			@Override
			public boolean hasNext() {
				if (index == -1)
					return false;
				else
					return true;
			}

			@Override
			public T next() {
				T value = (T) elementData[index];
				if (index == head)
					index = -1;
				else {
					index++;
					if (index == capacity) {
						index = 0;
					}
				}
				return value;
			}
		};
	}

	public Object[] toArray() {
		if (this.isEmpty())
			return new Object[0];
		Object[] result = new Object[this.size()];
		if (head >= tail) {
			System.arraycopy(elementData, tail, result, 0, this.size());
		} else {
			System.arraycopy(elementData, tail, result, 0, capacity - tail);
			System.arraycopy(elementData, 0, result, capacity - tail, head + 1);
		}
		return result;
	}

	public T[] toArray(T[] a) {
		if (this.isEmpty()) {
			if (a.length > 0) {
				a[0] = null;
			}
			return a;
		}
		if (a.length < this.size() && head >= tail) {
			return (T[]) Arrays.copyOfRange(elementData, tail, head + 1, a.getClass());
		} else {
			T[] result = (T[]) Array.newInstance(a.getClass().getComponentType(), this.size());
			System.arraycopy(elementData, tail, result, 0, capacity - tail);
			System.arraycopy(elementData, 0, result, capacity - tail, head + 1);
			return result;
		}
	}

	public void clear() {
		head = -1;
		tail = -1;
	}

	public void put(T e) {
		if (head == -1) {
			head = 0;
			tail = 0;
		} else if (head >= tail) {
			head++;
			if (head == capacity)
				head = 0;
		} else {
			head++;
			if (head == tail)
				tail++;
		}
		elementData[head] = e;
	}

	public T latest() {
		if (head == -1) {
			return null;
		}
		return (T) elementData[head];
	}

}
