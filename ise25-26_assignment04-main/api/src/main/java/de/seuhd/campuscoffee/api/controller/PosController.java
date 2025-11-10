package de.seuhd.campuscoffee.api.controller;

import de.seuhd.campuscoffee.api.dtos.PosDto;
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Controller for handling POS-related API requests.
 */
@Controller
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosController {
    private final PosService posService;
    private final PosDtoMapper posDtoMapper;

    @GetMapping("")
    public ResponseEntity<List<PosDto>> getAll() {
        return ResponseEntity.ok(
                posService.getAll().stream()
                        .map(posDtoMapper::fromDomain)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<PosDto> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                posDtoMapper.fromDomain(posService.getById(id))
        );
    }

    @PostMapping("")
    public ResponseEntity<PosDto> create(
            @RequestBody PosDto posDto) {
        PosDto created = upsert(posDto);
        return ResponseEntity
                .created(getLocation(created.id()))
                .body(created);
    }

    @PostMapping("/import/osm/{nodeId}")
    public ResponseEntity<PosDto> importFromOsm(
            @PathVariable Long nodeId) {
        // Check if a POS with the same name already exists before importing
        // We need to determine the name first by fetching the OSM node
        // For efficiency, we'll check after import and use the service's internal logic
        // The service already handles create/update, so we just need to determine the status code
        
        // Get all existing POS names before import
        var existingNames = posService.getAll().stream()
                .map(pos -> pos.name())
                .toList();
        
        // Import the POS (this will handle create/update internally based on name)
        PosDto imported = posDtoMapper.fromDomain(
                posService.importFromOsmNode(nodeId)
        );
        
        // Check if this was an update by seeing if the name existed before
        boolean isUpdate = existingNames.contains(imported.name());
        
        if (isUpdate) {
            // POS was updated (existed before)
            return ResponseEntity.ok(imported);
        } else {
            // POS was created (new)
            return ResponseEntity
                    .created(getLocation(imported.id()))
                    .body(imported);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PosDto> update(
            @PathVariable Long id,
            @RequestBody PosDto posDto) {
        if (!id.equals(posDto.id())) {
            throw new IllegalArgumentException("POS ID in path and body do not match.");
        }
        return ResponseEntity.ok(upsert(posDto));
    }

    /**
     * Common upsert logic for create and update.
     *
     * @param posDto the POS DTO to map and upsert
     * @return the upserted POS mapped back to the DTO format.
     */
    private PosDto upsert(PosDto posDto) {
        return posDtoMapper.fromDomain(
                posService.upsert(
                        posDtoMapper.toDomain(posDto)
                )
        );
    }

    /**
     * Builds the location URI for a newly created resource.
     * @param resourceId the ID of the created resource
     * @return the location URI
     */
    private URI getLocation(Long resourceId) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(resourceId)
                .toUri();
    }
}
