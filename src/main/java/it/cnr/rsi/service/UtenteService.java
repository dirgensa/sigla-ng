package it.cnr.rsi.service;

import it.cnr.rsi.domain.Messaggio;
import it.cnr.rsi.domain.Preferiti;
import it.cnr.rsi.domain.Utente;
import it.cnr.rsi.repository.AlberoMainRepository;
import it.cnr.rsi.repository.MessaggioRepository;
import it.cnr.rsi.repository.PreferitiRepository;
import it.cnr.rsi.repository.UtenteRepository;
import it.cnr.rsi.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class UtenteService implements UserDetailsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UtenteService.class);

    private UtenteRepository utenteRepository;
    private PreferitiRepository preferitiRepository;
    private AlberoMainRepository alberoMainRepository;
    private MessaggioRepository messaggioRepository;

    public UtenteService(UtenteRepository utenteRepository,
                         PreferitiRepository preferitiRepository,
                         AlberoMainRepository alberoMainRepository,
                         MessaggioRepository messaggioRepository) {
		super();
		this.utenteRepository = utenteRepository;
		this.preferitiRepository = preferitiRepository;
		this.alberoMainRepository = alberoMainRepository;
		this.messaggioRepository = messaggioRepository;
	}

    @Override
    public UserContext loadUserByUsername(String username) {
        return loadUserByUsername(username, "N");
    }

	@Transactional
	public UserContext loadUserByUsername(String username, String flAutenticazioneLdap) throws UsernameNotFoundException {
		LOGGER.info("Find user by username {}", username);
		return new UserContext(Optional.ofNullable(
				utenteRepository.findUserWithAuthenticationLDAP(
						Optional.ofNullable(username.toUpperCase()).orElse(""), flAutenticazioneLdap))
				.orElseThrow(() -> new UsernameNotFoundException("User not found")));
	}
	@Transactional
	public UserContext loadUserByUid(String uid) throws UsernameNotFoundException {
		LOGGER.info("Find user by uid {}", uid);
		return new UserContext(findUsersForUid(uid).stream().findAny().get());
	}

    @Transactional
    public List<Utente> findUsersForUid(String uid) throws UsernameNotFoundException {
        LOGGER.info("Find users by uid {}", uid);
        return utenteRepository.findUsersForUid(uid);
    }

    @Transactional
    public List<Preferiti> findPreferiti(String uid) {
        LOGGER.info("Find preferiti by uid {}", uid);
        return preferitiRepository.findPreferitiByUser(uid)
            .map(preferiti -> {
                preferiti.setCdNodo(
                    alberoMainRepository.findCdNodo(preferiti.getId().getBusinessProcess(), preferiti.getId().getTiFunzione())
                );
                return preferiti;
            }).sorted((preferiti, t1) ->
                preferiti.getDuva().compareTo(t1.getDuva())
            ).collect(Collectors.toList());
    }

    @Transactional
    public List<Messaggio> findMessaggi(String uid) {
        LOGGER.info("Find messaggi by uid {}", uid);
        return messaggioRepository.findMessaggiByUser(uid)
            .sorted((messaggio, t1) ->
                t1.getDacr().compareTo(messaggio.getDacr())
            ).collect(Collectors.toList());
    }

    @Transactional
    public List<Messaggio> deleteMessaggi(String uid, List<Messaggio> messaggi) {
        LOGGER.info("Delete messaggi by uid {}", uid);
        messaggi.stream()
            .filter(messaggio -> Optional.ofNullable(messaggio.getCdUtente()).filter(s -> s.equals(uid)).isPresent())
            .forEach(messaggio -> messaggioRepository.delete(messaggio));
        return findMessaggi(uid);
    }

}
