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

package hypergraph.concept;

import hypergraph.concept.type.EntityType;
import hypergraph.graph.GraphManager;
import hypergraph.graph.Schema;
import hypergraph.graph.Vertex;

public class ConceptManager {

    private final GraphManager graph;

    public ConceptManager(GraphManager graph) {
        this.graph = graph;
    }

    public EntityType putEntityType(String label) {
        return putEntityType(label, Schema.Vertex.Type.Root.ENTITY.label());
    }

    public EntityType putEntityType(String label, String parent) {
        Vertex.Type entityTypeVertex = graph.getVertexType(label);

        if (entityTypeVertex == null) {
            entityTypeVertex = graph.createVertexType(Schema.Vertex.Type.ENTITY_TYPE, label);
            Vertex.Type parentTypeVertex = graph.getVertexType(parent);
            graph.putEdge(Schema.Edge.SUB, entityTypeVertex, parentTypeVertex);
        }

        return new EntityType(entityTypeVertex);
    }
}
