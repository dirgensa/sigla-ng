package it.cnr.rsi.web;

import it.cnr.rsi.security.UserContext;
import it.cnr.rsi.service.EsercizioBaseService;
import it.cnr.rsi.service.UnitaOrganizzativaService;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by marco.spasiano on 23/03/17.
 */

@RestController
@RequestMapping("/api/context")
public class ContextResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextResource.class);

    public static final String API_ESERCIZIO = "/esercizio", API_UO = "/uo", API_CDS = "/cds";

    private EsercizioBaseService esercizioBaseService;
    private UnitaOrganizzativaService unitaOrganizzativaService;

    public ContextResource(EsercizioBaseService esercizioBaseService, UnitaOrganizzativaService unitaOrganizzativaService) {
        this.esercizioBaseService = esercizioBaseService;
        this.unitaOrganizzativaService = unitaOrganizzativaService;
    }


    @GetMapping(API_ESERCIZIO)
    public List<Integer> esercizi(){
    	UserContext userDetails = getUserDetails();
        LOGGER.info("GET esercizi for User: {}", userDetails.getUsername());
        return esercizioBaseService.findEsercizi();
    }

    @GetMapping(API_UO)
    public List<Pair<String, String>> findUnitaOrganizzativeAbilitate(String cds){
    	UserContext userDetails = getUserDetails();
        LOGGER.info("GET UO for User: {}", userDetails.getUsername());
        return unitaOrganizzativaService
        	.listaUnitaOrganizzativeAbilitate(userDetails.getUsername(), userDetails.getEsercizio(), cds)
        	.stream()
        	.map(x -> Pair.of(x.getCdUnitaOrganizzativa(), x.getDsUnitaOrganizzativa()))
        	.collect(Collectors.toList());
	}

    @GetMapping(API_CDS)
    public List<Pair<String, String>> findCdsAbilitati(String uo){
    	UserContext userDetails = getUserDetails();
        LOGGER.info("GET CDS for User: {}", userDetails.getUsername());
        return unitaOrganizzativaService
        	.listaCDSAbilitati(userDetails.getUsername(), userDetails.getEsercizio(), uo)
        	.stream()
        	.map(x -> Pair.of(x.getCdUnitaOrganizzativa(), x.getDsUnitaOrganizzativa()))
        	.collect(Collectors.toList());
	}
    
    @PostMapping
    public UserContext save(@RequestBody Map<String, ?> params){
    	UserContext userDetails = getUserDetails();
        LOGGER.info("POST params: {} for User: {}", params, userDetails.getUsername());
        params.forEach((k,v) -> userDetails.addAttribute(k, (Serializable) v));
        return userDetails;
    }

    
    public static UserContext getUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return Optional
            .ofNullable(authentication)
            .map(Authentication::getPrincipal)
            .filter(principal -> principal instanceof UserContext)
            .map(UserContext.class::cast)
            .orElseThrow(() -> new RuntimeException("something went wrong " + authentication.toString()));
    }

}
