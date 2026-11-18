
package com.alumni.linkedinsearcher.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "alumni", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_alumni_linkedin_url",
        columnNames = "linkedin_url"
    )
})
public class Alumni {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "current_designation", length = 250)
    private String currentRole;

    @Column(nullable = false, length = 250)
    private String university;

    @Column(length = 250)
    private String location;

    @Column(name = "linkedin_headline", columnDefinition = "TEXT")
    private String linkedinHeadline;

    @Column(name = "passout_year")
    private Integer passoutYear;

    @Column(name = "linkedin_url", length = 600)
    private String linkedinUrl;

    public Alumni() {}

    public Alumni(String name, String currentRole, String university,
                  String location, String linkedinHeadline,
                  Integer passoutYear, String linkedinUrl) {
        this.name = name;
        this.currentRole = currentRole;
        this.university = university;
        this.location = location;
        this.linkedinHeadline = linkedinHeadline;
        this.passoutYear = passoutYear;
        this.linkedinUrl = linkedinUrl;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCurrentRole() { return currentRole; }
    public String getUniversity() { return university; }
    public String getLocation() { return location; }
    public String getLinkedinHeadline() { return linkedinHeadline; }
    public Integer getPassoutYear() { return passoutYear; }
    public String getLinkedinUrl() { return linkedinUrl; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }
    public void setUniversity(String university) { this.university = university; }
    public void setLocation(String location) { this.location = location; }
    public void setLinkedinHeadline(String linkedinHeadline) { this.linkedinHeadline = linkedinHeadline; }
    public void setPassoutYear(Integer passoutYear) { this.passoutYear = passoutYear; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
}
