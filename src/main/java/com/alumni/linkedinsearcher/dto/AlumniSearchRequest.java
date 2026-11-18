package com.alumni.linkedinsearcher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


public class AlumniSearchRequest {
    @NotBlank(message = "University is required")
    @Size(max = 250)
    private String university;

    @NotBlank(message = "Designation is required")
    @Size(max = 250)
    private String designation;

    @Positive(message = "Pass-out year must be a positive number")
    private Integer passoutYear;

    public String getUniversity() { return university; }
    public String getDesignation() { return designation; }
    public Integer getPassoutYear() { return passoutYear; }

    public void setUniversity(String university) { this.university = university; }
    public void setDesignation(String designation) { this.designation = designation; }
    public void setPassoutYear(Integer passoutYear) { this.passoutYear = passoutYear; }
	@Override
	public String toString() {
		return "AlumniSearchRequest [university=" + university + ", designation=" + designation + ", passoutYear="
				+ passoutYear + "]";
	}
    
}
