/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.graph.internal.concept;

import ai.grakn.concept.Concept;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.graph.internal.cache.Cache;
import ai.grakn.graph.internal.cache.Cacheable;
import ai.grakn.graph.internal.structure.VertexElement;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.structure.Direction;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *     An ontological element which categorises how instances may relate to each other.
 * </p>
 *
 * <p>
 *     A relation type defines how {@link ai.grakn.concept.Type} may relate to one another.
 *     They are used to model and categorise n-ary relationships.
 * </p>
 *
 * @author fppt
 *
 */
public class RelationshipTypeImpl extends TypeImpl<RelationshipType, Relationship> implements RelationshipType {
    private final Cache<Set<Role>> cachedRelates = new Cache<>(Cacheable.set(), () -> this.<Role>neighbours(Direction.OUT, Schema.EdgeLabel.RELATES).collect(Collectors.toSet()));

    RelationshipTypeImpl(VertexElement vertexElement) {
        super(vertexElement);
    }

    RelationshipTypeImpl(VertexElement vertexElement, RelationshipType type, Boolean isImplicit) {
        super(vertexElement, type, isImplicit);
    }

    @Override
    public Relationship addRelationship() {
        return addInstance(Schema.BaseType.RELATIONSHIP,
                (vertex, type) -> vertex().graph().factory().buildRelation(vertex, type), true);
    }

    @Override
    public void txCacheFlush(){
        super.txCacheFlush();
        cachedRelates.flush();
    }

    @Override
    public void txCacheClear(){
        super.txCacheClear();
        cachedRelates.clear();
    }

    @Override
    public Stream<Role> relates() {
        return cachedRelates.get().stream();
    }

    @Override
    public RelationshipType relates(Role role) {
        checkOntologyMutationAllowed();
        putEdge(ConceptVertex.from(role), Schema.EdgeLabel.RELATES);

        //TODO: the following lines below this comment should only be executed if the edge is added

        //Cache the Role internally
        cachedRelates.ifPresent(set -> set.add(role));

        //Cache the relation type in the role
        ((RoleImpl) role).addCachedRelationType(this);

        //Put all the instance back in for tracking because their unique hashes need to be regenerated
        instances().forEach(instance -> vertex().graph().txCache().trackForValidation(instance));

        return this;
    }

    /**
     *
     * @param role The {@link Role} to delete from this {@link RelationshipType}.
     * @return The {@link Relationship} Type itself.
     */
    @Override
    public RelationshipType deleteRelates(Role role) {
        checkOntologyMutationAllowed();
        deleteEdge(Direction.OUT, Schema.EdgeLabel.RELATES, (Concept) role);

        RoleImpl roleTypeImpl = (RoleImpl) role;
        //Add roleplayers of role to make sure relations are still valid
        roleTypeImpl.rolePlayers().forEach(rolePlayer -> vertex().graph().txCache().trackForValidation(rolePlayer));


        //Add the Role Type itself
        vertex().graph().txCache().trackForValidation(roleTypeImpl);

        //Add the Relationship Type
        vertex().graph().txCache().trackForValidation(roleTypeImpl);

        //Remove from internal cache
        cachedRelates.ifPresent(set -> set.remove(role));

        //Remove from roleTypeCache
        ((RoleImpl) role).deleteCachedRelationType(this);

        //Put all the instance back in for tracking because their unique hashes need to be regenerated
        instances().forEach(instance -> vertex().graph().txCache().trackForValidation(instance));

        return this;
    }

    @Override
    public void delete(){
        //Force load the cache
        cachedRelates.get();

        super.delete();

        //Update the cache of the connected role types
        cachedRelates.get().forEach(r -> {
            RoleImpl role = ((RoleImpl) r);
            vertex().graph().txCache().trackForValidation(role);
            ((RoleImpl) r).deleteCachedRelationType(this);
        });
    }

    @Override
    void trackRolePlayers(){
        instances().forEach(concept -> {
            RelationshipImpl relation = RelationshipImpl.from(concept);
            if(relation.reified().isPresent()){
                relation.reified().get().castingsRelation().forEach(rolePlayer -> vertex().graph().txCache().trackForValidation(rolePlayer));
            }
        });
    }

    @Override
    public Stream<Relationship> instancesDirect(){
        Stream<Relationship> instances = super.instancesDirect();

        //If the relation type is implicit then we need to get any relation edges it may have.
        if(isImplicit()) instances = Stream.concat(instances, relationEdges());

        return instances;
    }

    private Stream<Relationship> relationEdges(){
        //Unfortunately this is a slow process
        return relates().
                flatMap(role -> role.playedByTypes()).
                flatMap(type ->{
                    //Traversal is used here to take advantage of vertex centric index
                    return  vertex().graph().getTinkerTraversal().V().
                            has(Schema.VertexProperty.ID.name(), type.getId().getValue()).
                            in(Schema.EdgeLabel.SHARD.getLabel()).
                            in(Schema.EdgeLabel.ISA.getLabel()).
                            outE(Schema.EdgeLabel.RESOURCE.getLabel()).
                            has(Schema.EdgeProperty.RELATIONSHIP_TYPE_LABEL_ID.name(), getLabelId().getValue()).
                            toStream().
                            map(edge -> vertex().graph().factory().buildConcept(edge).asRelationship());
                });
    }
}