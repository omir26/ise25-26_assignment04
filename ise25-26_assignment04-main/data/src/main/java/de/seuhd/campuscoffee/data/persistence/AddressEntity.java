package de.seuhd.campuscoffee.data.persistence;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AddressEntity {
    private String street;
    @Column(name = "house_number")
    private Integer houseNumber;
    @Column(name = "house_number_suffix")
    private Character houseNumberSuffix;
    @Column(name = "postal_code")
    private Integer postalCode;
    private String city;
}