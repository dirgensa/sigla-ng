/*
 * Copyright (C) 2019  Consiglio Nazionale delle Ricerche
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.cnr.rsi.repository;

import it.cnr.rsi.domain.UtenteUnitaRuolo;
import it.cnr.rsi.domain.UtenteUnitaRuoloPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

/**
 * Created by francesco on 07/03/17.
 */

@Repository
public interface UtenteUnitaRuoloRepository extends JpaRepository<UtenteUnitaRuolo, UtenteUnitaRuoloPK> {
	@Query(
	    "select id.cdRuolo from UtenteUnitaRuolo  " +
        " where id.cdUtente = :cdUtente " +
        " and id.cdUnitaOrganizzativa = :cdUnitaOrganizzativa"
    )
	Stream<String> findRuoliByCdUtente(@Param("cdUtente")String cdUtente, @Param("cdUnitaOrganizzativa")String cdUnitaOrganizzativa);
}
