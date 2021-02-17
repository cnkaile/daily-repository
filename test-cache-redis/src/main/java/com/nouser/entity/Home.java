package com.nouser.entity;

import java.util.List;

public class Home {
    private Person father;
    private Person mother;
    private List<Person> sons;
    private String address;


    public Person getFather() {
        return father;
    }

    public void setFather(Person father) {
        this.father = father;
    }

    public Person getMother() {
        return mother;
    }

    public void setMother(Person mother) {
        this.mother = mother;
    }

    public List<Person> getSons() {
        return sons;
    }

    public void setSons(List<Person> sons) {
        this.sons = sons;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
