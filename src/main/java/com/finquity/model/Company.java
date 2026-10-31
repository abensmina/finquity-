package com.finquity.model;

public class Company {
    private final int id;
    private String name;
    private final String siren;
    private String sector;

    public Company(int id, String name, String siren, String sector) {
        if (id < 0) {
            throw new IllegalArgumentException("L'identifiant doit être positif ou nul.");
        }
        if (siren == null || !siren.trim().matches("\\d{9}")) {
            throw new IllegalArgumentException("Le SIREN doit contenir exactement 9 chiffres.");
        }

        this.id = id;
        this.siren = siren.trim();
        setName(name);
        setSector(sector);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSiren() {
        return siren;
    }

    public String getSector() {
        return sector;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'entreprise est obligatoire.");
        }
        this.name = name.trim();
    }

    // Le secteur est facultatif (null accepté).
    public void setSector(String sector) {
        this.sector = (sector == null || sector.isBlank()) ? null : sector.trim();
    }

    @Override
    public String toString() {
        return "Company{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", siren='" + siren + '\'' +
                ", sector='" + sector + '\'' +
                '}';
    }
}
