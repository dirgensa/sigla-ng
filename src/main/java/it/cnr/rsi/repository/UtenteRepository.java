package it.cnr.rsi.repository;

import java.util.stream.Stream;

import it.cnr.rsi.domain.Utente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Created by francesco on 07/03/17.
 */

@Repository
public interface UtenteRepository extends JpaRepository<Utente, String> {
	@Query("select a from Utente a where cdUtente = :cdUtente AND flAutenticazioneLdap = :flAutenticazioneLdap")
	Utente findUserWithAuthenticationLDAP(@Param("cdUtente")String cdUtente, @Param("flAutenticazioneLdap")String flAutenticazioneLdap);		

	@Query("select a from Utente a where cdUtenteUid = :cdUtenteUid")
	Stream<Utente> findUsersForUid(@Param("cdUtenteUid")String cdUtenteUid);		

}