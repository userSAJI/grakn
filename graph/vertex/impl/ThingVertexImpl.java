/*
 * Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package hypergraph.graph.vertex.impl;

import hypergraph.common.exception.Error;
import hypergraph.common.exception.HypergraphException;
import hypergraph.graph.ThingGraph;
import hypergraph.graph.adjacency.Adjacency;
import hypergraph.graph.adjacency.ThingAdjacency;
import hypergraph.graph.adjacency.impl.ThingAdjacencyImpl;
import hypergraph.graph.edge.ThingEdge;
import hypergraph.graph.util.IID;
import hypergraph.graph.util.Schema;
import hypergraph.graph.vertex.ThingVertex;
import hypergraph.graph.vertex.TypeVertex;

public abstract class ThingVertexImpl
        extends VertexImpl<IID.Vertex.Thing, Schema.Vertex.Thing, ThingVertex, Schema.Edge.Thing, ThingEdge>
        implements ThingVertex {

    protected final ThingGraph graph;
    protected final ThingAdjacency outs;
    protected final ThingAdjacency ins;
    protected boolean isInferred;

    ThingVertexImpl(ThingGraph graph, IID.Vertex.Thing iid, boolean isInferred) {
        super(iid, iid.schema());
        this.graph = graph;
        this.outs = newAdjacency(Adjacency.Direction.OUT);
        this.ins = newAdjacency(Adjacency.Direction.IN);
        this.isInferred = isInferred;
    }

    /**
     * Instantiates a new {@code ThingAdjacency} class
     *
     * @param direction the direction of the edges held in {@code ThingAdjacency}
     * @return the new {@code ThingAdjacency} class
     */
    protected abstract ThingAdjacency newAdjacency(Adjacency.Direction direction);

    /**
     * Returns the {@code Graph} containing all {@code ThingVertex}
     *
     * @return the {@code Graph} containing all {@code ThingVertex}
     */
    @Override
    public ThingGraph graph() {
        return graph;
    }

    /**
     * Returns the {@code TypeVertex} in which this {@code ThingVertex} is an instance of
     *
     * @return the {@code TypeVertex} in which this {@code ThingVertex} is an instance of
     */
    @Override
    public TypeVertex typeVertex() {
        return graph.typeGraph().convert(iid.type());
    }

    /**
     * Returns true if this {@code ThingVertex} is a result of inference.
     *
     * @return true if this {@code ThingVertex} is a result of inference
     */
    @Override
    public boolean isInferred() {
        return isInferred;
    }

    @Override
    public Adjacency<Schema.Edge.Thing, ThingEdge, ThingVertex> outs() {
        return outs;
    }

    @Override
    public Adjacency<Schema.Edge.Thing, ThingEdge, ThingVertex> ins() {
        return ins;
    }

    public static class Buffered extends ThingVertexImpl {

        public Buffered(ThingGraph graph, IID.Vertex.Thing iid, boolean isInferred) {
            super(graph, iid, isInferred);
        }

        @Override
        protected ThingAdjacency newAdjacency(Adjacency.Direction direction) {
            return new ThingAdjacencyImpl.Buffered(this, direction);
        }

        @Override
        public void isInferred(boolean isInferred) {
            this.isInferred = isInferred;
        }

        @Override
        public Schema.Status status() {
            return Schema.Status.BUFFERED;
        }

        @Override
        public void commit(boolean hasAttributeSyncLock) {
            if (isInferred) throw new HypergraphException(Error.Transaction.ILLEGAL_OPERATION);
            graph.storage().put(iid.bytes());
            commitIndex();
            commitEdges(hasAttributeSyncLock);
        }

        private void commitIndex() {
            // TODO
        }

        private void commitEdges(boolean hasAttributeSyncLock) {
            outs.forEach(e -> e.commit(hasAttributeSyncLock));
            ins.forEach(e -> e.commit(hasAttributeSyncLock));
        }

        @Override
        public void delete() {
            // TODO
        }
    }

    public static class Persisted extends ThingVertexImpl {

        public Persisted(ThingGraph graph, IID.Vertex.Thing iid) {
            super(graph, iid, false);
        }

        @Override
        protected ThingAdjacency newAdjacency(Adjacency.Direction direction) {
            return new ThingAdjacencyImpl.Persisted(this, direction);
        }

        @Override
        public void isInferred(boolean isInferred) {
            throw new HypergraphException(Error.Transaction.ILLEGAL_OPERATION);
        }

        @Override
        public Schema.Status status() {
            return Schema.Status.PERSISTED;
        }

        @Override
        public void commit(boolean hasAttributeSyncLock) {
            commitEdges(hasAttributeSyncLock);
        }

        private void commitEdges(boolean hasAttributeSyncLock) {
            outs.forEach(e -> e.commit(hasAttributeSyncLock));
            ins.forEach(e -> e.commit(hasAttributeSyncLock));
        }

        @Override
        public void delete() {
            // TODO
        }
    }
}
