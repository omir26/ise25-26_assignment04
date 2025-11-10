# Implementierungsplan: OSM Node Import Feature

## Übersicht

Die Implementierung erfolgt in drei Hauptschritten, die aufeinander aufbauen:

1. **OsmNode Domain Model erweitern** - Datenstruktur für OSM-Tags hinzufügen
2. **OsmDataServiceImpl implementieren** - Echte OSM API Integration
3. **convertOsmNodeToPos() implementieren** - Mapping von OSM zu POS

---

## Schritt 1: OsmNode Domain Model erweitern

### Aktueller Zustand
```java
public record OsmNode(@NonNull Long nodeId) {
    // Nur nodeId vorhanden
}
```

### Was zu tun ist
Das `OsmNode` Record muss erweitert werden, um alle OSM-Tags zu speichern.

### Implementierung
```java
@Builder
public record OsmNode(
    @NonNull Long nodeId,
    @NonNull Map<String, String> tags,
    @Nullable Double latitude,
    @Nullable Double longitude
) {
    // Helper-Methoden für häufig verwendete Tags
    public String getName() {
        return tags.get("name");
    }
    
    public String getStreet() {
        return tags.get("addr:street");
    }
    
    // ... weitere Helper-Methoden
}
```

### Warum so?
- **Map<String, String> tags**: Speichert alle OSM-Tags flexibel (kann später erweitert werden)
- **latitude/longitude**: Optional, aber nützlich für zukünftige Features
- **Helper-Methoden**: Erleichtern den Zugriff auf häufig verwendete Tags
- **@Builder**: Bleibt konsistent mit bestehendem Code-Pattern

---

## Schritt 2: OsmDataServiceImpl - OSM API Integration

### Aktueller Zustand
```java
// Gibt nur hardcodierte Daten zurück
if (nodeId.equals(5589879349L)) {
    return OsmNode.builder().nodeId(nodeId).build();
}
```

### Was zu tun ist
1. HTTP GET Request an OSM API senden
2. XML Response parsen
3. Tags extrahieren
4. OsmNode mit allen Daten erstellen

### Implementierung (Pseudocode)

```java
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {
    
    private final RestTemplate restTemplate; // Oder WebClient
    
    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) 
            throws OsmNodeNotFoundException {
        
        // 1. URL zusammenbauen
        String url = "https://www.openstreetmap.org/api/0.6/node/" + nodeId;
        
        try {
            // 2. HTTP GET Request
            String xmlResponse = restTemplate.getForObject(url, String.class);
            
            // 3. XML parsen (z.B. mit JAXB, DOM Parser, oder einfachem String-Parsing)
            Document doc = parseXml(xmlResponse);
            
            // 4. Node-Element finden
            Element nodeElement = doc.getDocumentElement()
                .getElementsByTagName("node").item(0);
            
            // 5. Attribute extrahieren (id, lat, lon)
            Long id = Long.parseLong(nodeElement.getAttribute("id"));
            Double lat = parseDouble(nodeElement.getAttribute("lat"));
            Double lon = parseDouble(nodeElement.getAttribute("lon"));
            
            // 6. Tags extrahieren
            Map<String, String> tags = new HashMap<>();
            NodeList tagNodes = nodeElement.getElementsByTagName("tag");
            for (int i = 0; i < tagNodes.getLength(); i++) {
                Element tag = (Element) tagNodes.item(i);
                String key = tag.getAttribute("k");
                String value = tag.getAttribute("v");
                tags.put(key, value);
            }
            
            // 7. OsmNode erstellen
            return OsmNode.builder()
                .nodeId(id)
                .tags(tags)
                .latitude(lat)
                .longitude(lon)
                .build();
                
        } catch (HttpClientErrorException.NotFound e) {
            // 404 → Node existiert nicht
            throw new OsmNodeNotFoundException(nodeId);
        } catch (Exception e) {
            // Andere Fehler (Netzwerk, Parsing, etc.)
            log.error("Error fetching OSM node {}: {}", nodeId, e.getMessage());
            throw new OsmNodeNotFoundException(nodeId);
        }
    }
}
```

### Technische Details

**HTTP Client Wahl:**
- **RestTemplate**: Einfacher, aber deprecated in neueren Spring Versionen
- **WebClient**: Moderner, reaktiv, empfohlen für neue Projekte
- **Java HttpClient**: Standard Java, keine Spring-Abhängigkeit

**XML Parsing:**
- **DOM Parser**: Einfach, aber speicherintensiv für große XMLs
- **SAX Parser**: Effizienter, aber komplexer
- **JAXB**: Elegant, aber benötigt Schema
- **Einfaches String-Parsing**: Für einfache XMLs ausreichend

**Empfehlung:** RestTemplate + DOM Parser (einfachste Lösung)

---

## Schritt 3: convertOsmNodeToPos() - Mapping-Logik

### Aktueller Zustand
```java
// Nur hardcodiert für einen Node
if (osmNode.nodeId().equals(5589879349L)) {
    return Pos.builder()
        .name("Rada Coffee & Rösterei")
        // ... hardcodierte Werte
        .build();
}
```

### Was zu tun ist
OSM-Tags intelligent zu POS-Feldern mappen mit Validierung und Fehlerbehandlung.

### Implementierung (Pseudocode)

```java
private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
    Map<String, String> tags = osmNode.tags();
    
    // 1. Name extrahieren (required)
    String name = tags.get("name");
    if (name == null || name.isBlank()) {
        throw new OsmNodeMissingFieldsException(osmNode.nodeId());
    }
    
    // 2. Description extrahieren (optional)
    String description = tags.getOrDefault("description", 
        tags.getOrDefault("note", ""));
    
    // 3. PosType aus amenity bestimmen
    PosType posType = determinePosType(tags.get("amenity"));
    
    // 4. CampusType aus Adresse bestimmen
    CampusType campus = determineCampusType(
        tags.get("addr:postcode"),
        tags.get("addr:city")
    );
    
    // 5. Adressfelder extrahieren (alle required)
    String street = tags.get("addr:street");
    String houseNumber = tags.get("addr:housenumber");
    String postcodeStr = tags.get("addr:postcode");
    String city = tags.get("addr:city");
    
    // 6. Validierung
    validateRequiredFields(name, street, houseNumber, postcodeStr, city, 
        osmNode.nodeId());
    
    // 7. Postcode zu Integer parsen
    Integer postalCode;
    try {
        postalCode = Integer.parseInt(postcodeStr);
    } catch (NumberFormatException e) {
        log.warn("Invalid postal code '{}' for OSM node {}", 
            postcodeStr, osmNode.nodeId());
        throw new OsmNodeMissingFieldsException(osmNode.nodeId());
    }
    
    // 8. POS erstellen
    return Pos.builder()
        .name(name)
        .description(description)
        .type(posType)
        .campus(campus)
        .street(street)
        .houseNumber(houseNumber)
        .postalCode(postalCode)
        .city(city)
        .build();
}

// Helper-Methode: PosType bestimmen
private PosType determinePosType(String amenity) {
    if (amenity == null) {
        return PosType.CAFE; // Default
    }
    
    return switch (amenity.toLowerCase()) {
        case "cafe", "coffee_shop" -> PosType.CAFE;
        case "bakery" -> PosType.BAKERY;
        case "cafeteria", "canteen" -> PosType.CAFETERIA;
        case "vending_machine" -> PosType.VENDING_MACHINE;
        default -> {
            log.warn("Unknown amenity type: {}, defaulting to CAFE", amenity);
            yield PosType.CAFE;
        }
    };
}

// Helper-Methode: CampusType bestimmen
private CampusType determineCampusType(String postcode, String city) {
    // Einfache Heuristik basierend auf Postleitzahl
    if (postcode == null) {
        return CampusType.ALTSTADT; // Default
    }
    
    try {
        int code = Integer.parseInt(postcode);
        // Heidelberg Postleitzahlen: 69115-69126
        // Altstadt: ~69117
        // Bergheim: ~69115
        // INF: ~69120
        if (code >= 69115 && code <= 69126) {
            // Hier könnte man genauer mappen
            return CampusType.ALTSTADT; // Vereinfacht
        }
    } catch (NumberFormatException e) {
        // Ignorieren
    }
    
    return CampusType.ALTSTADT; // Default
}

// Helper-Methode: Validierung
private void validateRequiredFields(String name, String street, 
        String houseNumber, String postcode, String city, Long nodeId) {
    List<String> missing = new ArrayList<>();
    
    if (name == null || name.isBlank()) missing.add("name");
    if (street == null || street.isBlank()) missing.add("addr:street");
    if (houseNumber == null || houseNumber.isBlank()) 
        missing.add("addr:housenumber");
    if (postcode == null || postcode.isBlank()) missing.add("addr:postcode");
    if (city == null || city.isBlank()) missing.add("addr:city");
    
    if (!missing.isEmpty()) {
        log.error("OSM node {} missing required fields: {}", nodeId, missing);
        throw new OsmNodeMissingFieldsException(nodeId);
    }
}
```

### Warum so strukturiert?

1. **Separierte Helper-Methoden**: 
   - Bessere Testbarkeit
   - Klarere Struktur
   - Wiederverwendbarkeit

2. **Robuste Validierung**:
   - Prüft alle required Fields
   - Gibt klare Fehlermeldungen
   - Loggt Warnungen für fehlende optionale Felder

3. **Flexible Mapping-Logik**:
   - Default-Werte für fehlende Daten
   - Fallback-Mechanismen
   - Erweiterbar für neue OSM-Tags

---

## Abhängigkeiten prüfen

### Benötigte Dependencies

Für XML-Parsing:
```xml
<!-- Bereits vorhanden durch spring-boot-starter-web -->
<!-- Falls nicht: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Für RestTemplate (falls nicht vorhanden):
```xml
<!-- Bereits in spring-boot-starter-web enthalten -->
```

Optional - für besseres XML-Parsing:
```xml
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
</dependency>
```

---

## Test-Strategie

### Unit Tests
1. **OsmDataServiceImpl**:
   - Mock HTTP-Response
   - Test XML-Parsing
   - Test Error-Handling (404, Netzwerkfehler)

2. **convertOsmNodeToPos()**:
   - Test mit vollständigen OSM-Daten
   - Test mit fehlenden required Fields
   - Test mit verschiedenen amenity-Typen
   - Test CampusType-Bestimmung

### Integration Tests
- Test mit echten OSM Node IDs (5589879349)
- Test mit nicht-existierenden Node IDs
- Test End-to-End (API-Call → Database)

---

## Reihenfolge der Implementierung

**Empfohlene Reihenfolge:**

1. ✅ **OsmNode erweitern** (einfachste Änderung, keine Abhängigkeiten)
2. ✅ **convertOsmNodeToPos() implementieren** (kann mit Mock-Daten getestet werden)
3. ✅ **OsmDataServiceImpl implementieren** (benötigt beide vorherigen Schritte)

**Warum diese Reihenfolge?**
- Schritt 1 ist unabhängig und kann sofort gemacht werden
- Schritt 2 kann mit Mock-OsmNode-Objekten getestet werden
- Schritt 3 integriert alles zusammen

---

## Mögliche Herausforderungen

### 1. XML-Parsing
**Problem**: OSM XML kann komplex sein  
**Lösung**: DOM Parser verwenden, robuste Fehlerbehandlung

### 2. Fehlende OSM-Tags
**Problem**: Nicht alle OSM-Nodes haben alle benötigten Tags  
**Lösung**: Klare Validierung, aussagekräftige Fehlermeldungen

### 3. CampusType-Bestimmung
**Problem**: Postleitzahl allein reicht nicht immer  
**Lösung**: Heuristik implementieren, Default-Wert verwenden

### 4. Netzwerk-Fehler
**Problem**: OSM API könnte nicht erreichbar sein  
**Lösung**: Timeouts setzen, Fehler loggen, Exception werfen

---

## Zusammenfassung

Die Implementierung erfolgt in drei logischen Schritten:

1. **Datenmodell erweitern** → OsmNode kann alle Tags speichern
2. **Externe API integrieren** → Echte OSM-Daten werden abgerufen
3. **Mapping implementieren** → OSM-Daten werden zu POS-Objekten konvertiert

Jeder Schritt baut auf dem vorherigen auf und kann einzeln getestet werden.

