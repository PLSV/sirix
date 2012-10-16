/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sirix.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.sirix.index.path.PathNode;
import org.sirix.index.value.AVLNode;
import org.sirix.node.delegates.NameNodeDelegate;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.delegates.ValNodeDelegate;
import org.sirix.node.interfaces.NodeBase;
import org.sirix.node.interfaces.NodeKind;
import org.sirix.page.NodePage;
import org.sirix.service.xml.xpath.AtomicValue;
import org.sirix.settings.Fixed;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

/**
 * Enumeration for different nodes. All nodes are determined by a unique id.
 * 
 * @author Sebastian Graf, University of Konstanz
 * @author Johannes Lichtenberger, University of Konstanz
 * 
 */
public enum Kind implements NodeKind {

	/** Node kind is element. */
	ELEMENT((byte) 1, ElementNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Struct delegate.
			final StructNodeDelegate structDel = deserializeStructDel(nodeDel, source);

			// Name delegate.
			final NameNodeDelegate nameDel = deserializeNameDelegate(nodeDel, source);

			// Attributes.
			final int attrCount = source.readInt();
			final List<Long> attrKeys = new ArrayList<>(attrCount);
			final BiMap<Integer, Long> attrs = HashBiMap.<Integer, Long> create();
			for (int i = 0; i < attrCount; i++) {
				final long nodeKey = source.readLong();
				attrKeys.add(nodeKey);
				attrs.put(source.readInt(), nodeKey);
			}

			// Namespaces.
			final int nsCount = source.readInt();
			final List<Long> namespKeys = new ArrayList<>(nsCount);
			for (int i = 0; i < nsCount; i++) {
				namespKeys.add(source.readLong());
			}

			return new ElementNode(structDel, nameDel, attrKeys, attrs, namespKeys);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final ElementNode node = (ElementNode) toSerialize;
			serializeDelegate(node.getNodeDelegate(), sink);
			serializeStrucDelegate(node.getStructNodeDelegate(), sink);
			serializeNameDelegate(node.getNameNodeDelegate(), sink);
			sink.writeInt(node.getAttributeCount());
			for (int i = 0, attCount = node.getAttributeCount(); i < attCount; i++) {
				final long key = node.getAttributeKey(i);
				sink.writeLong(key);
				sink.writeInt(node.getAttributeNameKey(key).get());
			}
			sink.writeInt(node.getNamespaceCount());
			for (int i = 0, nspCount = node.getNamespaceCount(); i < nspCount; i++) {
				sink.writeLong(node.getNamespaceKey(i));
			}
		}
	},

	/** Node kind is attribute. */
	ATTRIBUTE((byte) 2, AttributeNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Name delegate.
			final NameNodeDelegate nameDel = deserializeNameDelegate(nodeDel, source);

			// Val delegate.
			final boolean isCompressed = source.readByte() == (byte) 1 ? true : false;
			final byte[] vals = new byte[source.readInt()];
			source.readFully(vals, 0, vals.length);
			final ValNodeDelegate valDel = new ValNodeDelegate(nodeDel, vals,
					isCompressed);

			// Returning an instance.
			return new AttributeNode(nodeDel, nameDel, valDel);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final AttributeNode node = (AttributeNode) toSerialize;
			serializeDelegate(node.getNodeDelegate(), sink);
			serializeNameDelegate(node.getNameNodeDelegate(), sink);
			serializeValDelegate(node.getValNodeDelegate(), sink);
		}
	},

	/** Node kind is namespace. */
	NAMESPACE((byte) 13, NamespaceNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Name delegate.
			final NameNodeDelegate nameDel = deserializeNameDelegate(nodeDel, source);

			return new NamespaceNode(nodeDel, nameDel);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final NamespaceNode node = (NamespaceNode) toSerialize;
			serializeDelegate(node.getNodeDelegate(), sink);
			serializeNameDelegate(node.getNameNodeDelegate(), sink);
		}
	},

	/** Node kind is text. */
	TEXT((byte) 3, TextNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Val delegate.
			final boolean isCompressed = source.readByte() == (byte) 1 ? true : false;
			final byte[] vals = new byte[source.readInt()];
			source.readFully(vals, 0, vals.length);
			final ValNodeDelegate valDel = new ValNodeDelegate(nodeDel, vals,
					isCompressed);

			// Struct delegate.
			final long nodeKey = nodeDel.getNodeKey();
			final StructNodeDelegate structDel = new StructNodeDelegate(nodeDel,
					Fixed.NULL_NODE_KEY.getStandardProperty(),
					nodeKey - getLong(source), nodeKey - getLong(source), 0L, 0L);

			// Returning an instance.
			return new TextNode(valDel, structDel);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final TextNode node = (TextNode) toSerialize;
			serializeDelegate(node.getNodeDelegate(), sink);
			serializeValDelegate(node.getValNodeDelegate(), sink);
			final StructNodeDelegate del = node.getStructNodeDelegate();
			final long nodeKey = node.getNodeKey();
			putLong(sink, nodeKey - del.getRightSiblingKey());
			putLong(sink, nodeKey - del.getLeftSiblingKey());
		}
	},

	/** Node kind is processing instruction. */
	PROCESSING((byte) 7, PINode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Struct delegate.
			final StructNodeDelegate structDel = deserializeStructDel(nodeDel, source);

			// Name delegate.
			final NameNodeDelegate nameDel = deserializeNameDelegate(nodeDel, source);

			// Val delegate.
			final boolean isCompressed = source.readByte() == (byte) 1 ? true : false;
			final byte[] vals = new byte[source.readInt()];
			source.readFully(vals, 0, vals.length);
			final ValNodeDelegate valDel = new ValNodeDelegate(nodeDel, vals,
					isCompressed);

			// Returning an instance.
			return new PINode(structDel, nameDel, valDel);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput pSink,
				final @Nonnull NodeBase pToSerialize) {
			final PINode node = (PINode) pToSerialize;
			serializeDelegate(node.getNodeDelegate(), pSink);
			serializeStrucDelegate(node.getStructNodeDelegate(), pSink);
			serializeNameDelegate(node.getNameNodeDelegate(), pSink);
			serializeValDelegate(node.getValNodeDelegate(), pSink);
		}
	},

	/** Node kind is comment. */
	COMMENT((byte) 8, CommentNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Val delegate.
			final boolean isCompressed = source.readByte() == (byte) 1 ? true : false;
			final byte[] vals = new byte[source.readInt()];
			source.readFully(vals, 0, vals.length);
			final ValNodeDelegate valDel = new ValNodeDelegate(nodeDel, vals,
					isCompressed);

			// Struct delegate.
			final long nodeKey = nodeDel.getNodeKey();
			final StructNodeDelegate structDel = new StructNodeDelegate(nodeDel,
					Fixed.NULL_NODE_KEY.getStandardProperty(),
					nodeKey - getLong(source), nodeKey - getLong(source), 0L, 0L);

			// Returning an instance.
			return new CommentNode(valDel, structDel);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final CommentNode node = (CommentNode) toSerialize;
			serializeDelegate(node.getNodeDelegate(), sink);
			serializeValDelegate(node.getValNodeDelegate(), sink);
			final StructNodeDelegate del = node.getStructNodeDelegate();
			final long nodeKey = node.getNodeKey();
			putLong(sink, nodeKey - del.getRightSiblingKey());
			putLong(sink, nodeKey - del.getLeftSiblingKey());
		}
	},

	/** Node kind is document root. */
	// Virtualize document root node?
	DOCUMENT_ROOT((byte) 9, DocumentRootNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			final NodeDelegate nodeDel = new NodeDelegate(
					Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
					Fixed.NULL_NODE_KEY.getStandardProperty(), source.readLong(),
					getLong(source));
			final StructNodeDelegate structDel = new StructNodeDelegate(nodeDel,
					getLong(source), Fixed.NULL_NODE_KEY.getStandardProperty(),
					Fixed.NULL_NODE_KEY.getStandardProperty(),
					source.readByte() == ((byte) 0) ? 0 : 1, source.readLong());
			return new DocumentRootNode(nodeDel, structDel);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput pSink,
				final @Nonnull NodeBase pToSerialize) {
			final DocumentRootNode node = (DocumentRootNode) pToSerialize;
			pSink.writeLong(node.getHash());
			putLong(pSink, node.getRevision());
			putLong(pSink, node.getFirstChildKey());
			pSink.writeByte(node.hasFirstChild() ? (byte) 1 : (byte) 0);
			pSink.writeLong(node.getDescendantCount());
		}
	},

	/** Whitespace text. */
	WHITESPACE((byte) 4, null) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase pToSerialize) {
			throw new UnsupportedOperationException();
		}
	},

	/** Node kind is deleted node. */
	DELETE((byte) 5, DeletedNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			final NodeDelegate delegate = new NodeDelegate(getLong(source), 0, 0, 0);
			return new DeletedNode(delegate);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase pToSerialize) {
			DeletedNode node = (DeletedNode) pToSerialize;
			putLong(sink, node.getNodeKey());
		}
	},

	/** NullNode to support the Null Object pattern. */
	NULL((byte) 6, NullNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput ink,
				final @Nonnull NodeBase toSerialize) {
			throw new UnsupportedOperationException();
		}
	},

	/** Dumb node for testing. */
	DUMB((byte) 20, DumbNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			final long nodeKey = getLong(source);
			return new DumbNode(nodeKey);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			putLong(sink, toSerialize.getNodeKey());
		}
	},

	/** AtomicKind. */
	ATOMIC((byte) 15, AtomicValue.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase pToSerialize) {
			throw new UnsupportedOperationException();
		}
	},

	/** Node kind is path node. */
	PATH((byte) 16, PathNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);

			// Struct delegate.
			final StructNodeDelegate structDel = deserializeStructDel(nodeDel, source);

			// Name delegate.
			final NameNodeDelegate nameDel = deserializeNameDelegate(nodeDel, source);

			return new PathNode(nodeDel, structDel, nameDel, Kind.getKind(source
					.readByte()), source.readInt(), source.readInt());
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final PathNode node = (PathNode) toSerialize;
			serializeDelegate(node.getNodeDelegate(), sink);
			serializeStrucDelegate(node.getStructNodeDelegate(), sink);
			serializeNameDelegate(node.getNameNodeDelegate(), sink);
			sink.writeByte(node.getPathKind().getId());
			sink.writeInt(node.getReferences());
			sink.writeInt(node.getLevel());
		};
	},

	/** Node kind is an AVL node. */
	AVL((byte) 17, AVLNode.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			final int size = source.readInt();
			final byte[] value = new byte[size];
			source.readFully(value, 0, size);
			final long valueNodeKey = getLong(source);
			final Set<Long> nodeKeys = new HashSet<>(source.readInt());
			for (final long nodeKey : nodeKeys) {
				nodeKeys.add(nodeKey);
			}
			final long referencesNodeKey = getLong(source);
			// Node delegate.
			final NodeDelegate nodeDel = deserializeNodeDelegate(source);
			final long leftChild = getLong(source);
			final long rightChild = getLong(source);
			final long pathNodeKey = getLong(source);
			final boolean isChanged = source.readBoolean();
			final AVLNode<TextValue, TextReferences> node = new AVLNode<>(
					new TextValue(value, valueNodeKey, pathNodeKey), new TextReferences(
							nodeKeys, referencesNodeKey), nodeDel);
			node.setLeftChildKey(leftChild);
			node.setRightChildKey(rightChild);
			node.setChanged(isChanged);
			return node;
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			@SuppressWarnings("unchecked")
			final AVLNode<TextValue, TextReferences> node = (AVLNode<TextValue, TextReferences>) toSerialize;
			final TextValue key = node.getKey();
			final byte[] textValue = key.getValue();
			sink.writeInt(textValue.length);
			sink.write(textValue);
			putLong(sink, key.getNodeKey());
			final TextReferences value = node.getValue();
			final Set<Long> nodeKeys = value.getNodeKeys();
			sink.writeInt(nodeKeys.size());
			for (final long nodeKey : nodeKeys) {
				sink.writeLong(nodeKey);
			}
			putLong(sink, value.getNodeKey());
			serializeDelegate(node.getNodeDelegate(), sink);
			putLong(sink, node.getLeftChildKey());
			putLong(sink, node.getRightChildKey());
			putLong(sink, key.getPathNodeKey());
			sink.writeBoolean(node.isChanged());
		};
	},

	/** Node is a text value. */
	TEXT_VALUE((byte) 18, TextValue.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			final long nodeKey = getLong(source);
			final long pathNodeKey = getLong(source);
			final byte[] value = new byte[source.readInt()];
			source.readFully(value);
			return new TextValue(value, nodeKey, pathNodeKey);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final TextValue node = (TextValue) toSerialize;
			putLong(sink, node.getNodeKey());
			putLong(sink, node.getPathNodeKey());
			final byte[] value = node.getValue();
			sink.writeInt(value.length);
			sink.write(value);
		}
	},

	/** Node includes text node references. */
	TEXT_REFERENCES((byte) 19, TextReferences.class) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			final long nodeKey = source.readLong();
			final int size = source.readInt();
			final Set<Long> nodeKeys = new HashSet<>(size);
			for (int i = 0; i < size; i++) {
				nodeKeys.add(source.readLong());
			}
			return new TextReferences(nodeKeys, nodeKey);
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			final TextReferences node = (TextReferences) toSerialize;
			sink.writeLong(node.getNodeKey());
			final Set<Long> nodeKeys = node.getNodeKeys();
			sink.writeInt(nodeKeys.size());
			for (final long key : nodeKeys) {
				sink.writeLong(key);
			}
		}
	},

	/** Node type not known. */
	UNKNOWN((byte) 21, null) {
		@Override
		public NodeBase deserialize(final @Nonnull ByteArrayDataInput source) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void serialize(final @Nonnull ByteArrayDataOutput sink,
				final @Nonnull NodeBase toSerialize) {
			throw new UnsupportedOperationException();
		}
	};

	/** Identifier. */
	private final byte mId;

	/** Class. */
	private final Class<? extends NodeBase> mClass;

	/** Mapping of keys -> nodes. */
	private static final Map<Byte, Kind> INSTANCEFORID = new HashMap<>();

	/** Mapping of class -> nodes. */
	private static final Map<Class<? extends NodeBase>, Kind> INSTANCEFORCLASS = new HashMap<>();

	static {
		for (final Kind node : values()) {
			INSTANCEFORID.put(node.mId, node);
			INSTANCEFORCLASS.put(node.mClass, node);
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param pId
	 *          unique identifier
	 * @param clazz
	 *          class
	 */
	private Kind(final byte pId, final @Nonnull Class<? extends NodeBase> clazz) {
		mId = pId;
		mClass = clazz;
	}

	@Override
	public byte getId() {
		return mId;
	}

	@Override
	public Class<? extends NodeBase> getNodeClass() {
		return mClass;
	}

	/**
	 * Public method to get the related node based on the identifier.
	 * 
	 * @param id
	 *          the identifier for the node
	 * @return the related node
	 */
	public static Kind getKind(final byte id) {
		return INSTANCEFORID.get(id);
	}

	/**
	 * Public method to get the related node based on the class.
	 * 
	 * @param clazz
	 *          the class for the node
	 * @return the related node
	 */
	public static Kind getKind(final @Nonnull Class<? extends NodeBase> clazz) {
		return INSTANCEFORCLASS.get(clazz);
	}

	/**
	 * Deserialize node delegate.
	 * 
	 * @param pUsePCR
	 *          determines if PCR is saved (for attributes, namespaces and
	 *          elements) or not
	 * @param source
	 *          source to read from
	 * @return {@link NodeDelegate} instance
	 */
	private static final NodeDelegate deserializeNodeDelegate(
			final @Nonnull ByteArrayDataInput source) {
		final long nodeKey = getLong(source);
		final long parentKey = nodeKey - getLong(source);
		final long hash = source.readLong();
		final long revision = getLong(source);
		return new NodeDelegate(nodeKey, parentKey, hash, revision);
	}

	/**
	 * Serializing the {@link NodeDelegate} instance.
	 * 
	 * @param nodeDel
	 *          to be serialize
	 * @param sink
	 *          to serialize to
	 */
	private static final void serializeDelegate(final @Nonnull NodeDelegate nodeDel,
			final @Nonnull ByteArrayDataOutput sink) {
		putLong(sink, nodeDel.getNodeKey());
		putLong(sink, nodeDel.getNodeKey() - nodeDel.getParentKey());
		sink.writeLong(nodeDel.getHash());
		putLong(sink, nodeDel.getRevision());
	}

	/**
	 * Serializing the {@link StructNodeDelegate} instance.
	 * 
	 * @param nodeDel
	 *          to be serialize
	 * @param sink
	 *          to serialize to
	 */
	private static final void serializeStrucDelegate(
			final @Nonnull StructNodeDelegate nodeDel,
			final @Nonnull ByteArrayDataOutput sink) {
		putLong(sink, nodeDel.getNodeKey() - nodeDel.getRightSiblingKey());
		putLong(sink, nodeDel.getNodeKey() - nodeDel.getLeftSiblingKey());
		putLong(sink, nodeDel.getNodeKey() - nodeDel.getFirstChildKey());
		putLong(sink, nodeDel.getChildCount());
		putLong(sink, nodeDel.getDescendantCount() - nodeDel.getChildCount());
	}

	/**
	 * Deserialize struct delegate.
	 * 
	 * @param nodeDel
	 *          node delegate
	 * @param source
	 *          input source
	 * @return {@link StructNodeDelegate} instance
	 */
	private static final StructNodeDelegate deserializeStructDel(
			final @Nonnull NodeDelegate nodeDel,
			final @Nonnull ByteArrayDataInput source) {
		final long currKey = nodeDel.getNodeKey();
		final long rightSibl = currKey - getLong(source);
		final long leftSibl = currKey - getLong(source);
		final long firstChild = currKey - getLong(source);
		final long childCount = getLong(source);
		final long descendantCount = getLong(source) + childCount;
		return new StructNodeDelegate(nodeDel, firstChild, rightSibl, leftSibl,
				childCount, descendantCount);
	}

	/**
	 * Deserialize name node delegate.
	 * 
	 * @param nodeDel
	 *          {@link NodeDelegate} instance
	 * @param source
	 *          source to read from
	 * @return {@link NameNodeDelegate} instance
	 */
	private static final NameNodeDelegate deserializeNameDelegate(
			final @Nonnull NodeDelegate nodeDel,
			final @Nonnull ByteArrayDataInput source) {
		int nameKey = source.readInt();
		final int uriKey = source.readInt();
		nameKey += uriKey;
		return new NameNodeDelegate(nodeDel, nameKey, uriKey, getLong(source));
	}

	/**
	 * Serializing the {@link NameNodeDelegate} instance.
	 * 
	 * @param nameDel
	 *          {@link NameNodeDelegate} instance
	 * @param sink
	 *          to serialize to
	 */
	private static final void serializeNameDelegate(
			final @Nonnull NameNodeDelegate nameDel,
			final @Nonnull ByteArrayDataOutput sink) {
		sink.writeInt(nameDel.getNameKey() - nameDel.getURIKey());
		sink.writeInt(nameDel.getURIKey());
		putLong(sink, nameDel.getPathNodeKey());
	}

	/**
	 * Serializing the {@link ValNodeDelegate} instance.
	 * 
	 * @param valueDel
	 *          to be serialized
	 * @param sink
	 *          to serialize to
	 */
	private static final void serializeValDelegate(
			final @Nonnull ValNodeDelegate valueDel,
			final @Nonnull ByteArrayDataOutput sink) {
		final boolean isCompressed = valueDel.isCompressed();
		sink.writeByte(isCompressed ? (byte) 1 : (byte) 0);
		final byte[] value = isCompressed ? valueDel.getCompressed() : valueDel
				.getRawValue();
		sink.writeInt(value.length);
		sink.write(value);
	}

	/**
	 * Store a compressed long value.
	 * 
	 * @param pOutput
	 *          {@link ByteArrayDataOutput} reference
	 * @param value
	 *          long value
	 */
	private static final void putLong(final @Nonnull ByteArrayDataOutput pOutput,
			long value) {
		while ((value & ~0x7F) != 0) {
			pOutput.write(((byte) ((value & 0x7f) | 0x80)));
			value >>>= 7;
		}
		pOutput.write((byte) value);
	}

	/**
	 * Get a compressed long value.
	 * 
	 * @param input
	 *          {@link ByteArrayDataInput} reference
	 * @return long value
	 */
	private static final long getLong(final @Nonnull ByteArrayDataInput input) {
		byte singleByte = input.readByte();
		long value = singleByte & 0x7F;
		for (int shift = 7; (singleByte & 0x80) != 0; shift += 7) {
			singleByte = input.readByte();
			value |= (singleByte & 0x7FL) << shift;
		}
		return value;
	}

	/**
	 * Simple DumbNode just for testing the {@link NodePage}s.
	 * 
	 * @author Sebastian Graf, University of Konstanz
	 * @author Johannes Lichtenberger
	 * 
	 */
	public static class DumbNode implements NodeBase {

		/** Node key. */
		private final long mNodeKey;

		/**
		 * Simple constructor.
		 * 
		 * @param nodeKey
		 *          to be set
		 * @param pHash
		 *          to be set
		 */
		public DumbNode(final @Nonnull long nodeKey) {
			mNodeKey = nodeKey;
		}

		@Override
		public long getNodeKey() {
			return mNodeKey;
		}

		@Override
		public Kind getKind() {
			return Kind.NULL;
		}

		@Override
		public long getRevision() {
			return 0;
		}
	}
}
