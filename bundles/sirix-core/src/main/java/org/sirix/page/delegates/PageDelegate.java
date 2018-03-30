/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: * Redistributions of source code must retain the
 * above copyright notice, this list of conditions and the following disclaimer. * Redistributions
 * in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sirix.page.delegates;

import static com.google.common.base.Preconditions.checkArgument;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nonnegative;
import org.magicwerk.brownies.collections.GapList;
import org.sirix.api.PageWriteTrx;
import org.sirix.node.interfaces.Record;
import org.sirix.page.DeserializedTuple;
import org.sirix.page.PageReference;
import org.sirix.page.SerializationType;
import org.sirix.page.interfaces.KeyValuePage;
import org.sirix.page.interfaces.Page;
import org.sirix.settings.Constants;
import com.google.common.base.MoreObjects;

/**
 * <h1>PageDelegate</h1>
 *
 * <p>
 * Class to provide basic reference handling functionality.
 * </p>
 */
public final class PageDelegate implements Page {

  /** Page references. */
  private List<PageReference> mReferences;

  /** The bitmap to use, which indexes are null/not null in the references array. */
  private BitSet mBitmap;

  /**
   * Constructor to initialize instance.
   *
   * @param referenceCount number of references of page
   */
  public PageDelegate(final @Nonnegative int referenceCount) {
    checkArgument(referenceCount >= 0);
    mReferences = new GapList<>();
    mBitmap = new BitSet(referenceCount);
  }

  /**
   * Constructor to initialize instance.
   *
   * @param referenceCount number of references of page
   * @param in input stream to read from
   * @param type the serialization type
   * @throws IOException if the delegate couldn't be deserialized
   */
  public PageDelegate(final @Nonnegative int referenceCount, final DataInput in,
      final SerializationType type) {
    final DeserializedTuple tuple = type.deserialize(referenceCount, in);
    mReferences = tuple.getReferences();
    mBitmap = tuple.getBitmap();
  }

  /**
   * Constructor to initialize instance.
   *
   * @param commitedPage commited page
   */
  public PageDelegate(final Page commitedPage, final BitSet bitSet) {
    mBitmap = (BitSet) bitSet.clone();

    final int length = commitedPage.getReferences().size();

    mReferences = new GapList<>(length);

    for (int offset = 0; offset < length; offset++) {
      final PageReference reference = new PageReference();
      reference.setKey(commitedPage.getReferences().get(offset).getKey());
      mReferences.add(offset, reference);
    }
  }

  @Override
  public List<PageReference> getReferences() {
    return mReferences;
  }

  public BitSet getBitmap() {
    return (BitSet) mBitmap.clone();
  }

  /**
   * Get page reference of given offset.
   *
   * @param offset offset of page reference
   * @return {@link PageReference} at given offset
   */
  @Override
  public final PageReference getReference(final @Nonnegative int offset) {
    final BitSet offsetBitmap = new BitSet(mBitmap.size());

    offsetBitmap.set(offset);
    offsetBitmap.and(mBitmap);

    if (offsetBitmap.cardinality() != 0) {
      final int index = index(offsetBitmap, offset);
      return mReferences.get(index);
    } else {
      return createNewReference(offsetBitmap, offset);
    }
  }

  private PageReference createNewReference(final BitSet offsetBitmap, int offset) {
    final int index = index(offsetBitmap, offset);

    final PageReference reference = new PageReference();
    mReferences.add(index, reference);
    // final PageReference[] newArray = new PageReference[mReferences.length + 1];
    // System.arraycopy(mReferences, 0, newArray, 0, index);
    // newArray[index] = new PageReference();
    // System.arraycopy(mReferences, index, newArray, index + 1, mReferences.length - index);

    mBitmap.set(offset, true);
    // mReferences = newArray;
    return reference;
  }

  private int index(BitSet bitmap, int offset) {
    // Flip 0 to offset.
    bitmap.flip(0, offset + 1);

    bitmap.and(mBitmap);

    return bitmap.cardinality();
  }

  /**
   * Recursively call commit on all referenced pages.
   *
   * @param pageWriteTransaction the page write transaction
   */
  @Override
  public final <K extends Comparable<? super K>, V extends Record, S extends KeyValuePage<K, V>> void commit(
      final PageWriteTrx<K, V, S> pageWriteTrx) {
    for (final PageReference reference : mReferences) {
      if (!(reference.getLogKey() == Constants.NULL_ID_INT
          && reference.getPersistentLogKey() == Constants.NULL_ID_LONG)) {
        pageWriteTrx.commit(reference);
      }
    }
  }

  /**
   * Serialize page references into output.
   *
   * @param out output stream
   * @param serializationType the type to serialize (transaction intent log or the data file
   *        itself).
   */
  @Override
  public void serialize(final DataOutput out, final SerializationType type) {
    assert out != null;
    assert type != null;

    type.serialize(out, mReferences, mBitmap);
  }

  @Override
  public String toString() {
    final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
    for (final PageReference ref : mReferences) {
      helper.add("reference", ref);
    }
    helper.add("bitmap", dumpBitmap(mBitmap));
    return helper.toString();
  }

  private String dumpBitmap(BitSet bitmap) {
    final StringBuilder s = new StringBuilder();

    for (int i = 0; i < bitmap.length(); i++) {
      s.append(
          bitmap.get(i) == true
              ? 1
              : 0);
    }

    return s.toString();
  }
}
