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
 *
 */

package ai.grakn.graql.internal.gremlin.sets;

import ai.grakn.graql.Var;
import ai.grakn.graql.internal.gremlin.EquivalentFragmentSet;

import static ai.grakn.graql.internal.gremlin.fragment.Fragments.inRolePlayer;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.outRolePlayer;

/**
 * @author Felix Chapman
 */
class RolePlayerFragmentSet extends EquivalentFragmentSet {

    private final Var casting;
    private final Var rolePlayer;

    RolePlayerFragmentSet(Var casting, Var rolePlayer) {
        super(outRolePlayer(casting, rolePlayer), inRolePlayer(rolePlayer, casting));
        this.casting = casting;
        this.rolePlayer = rolePlayer;
    }

    public Var casting() {
        return casting;
    }

    public Var rolePlayer() {
        return rolePlayer;
    }
}
