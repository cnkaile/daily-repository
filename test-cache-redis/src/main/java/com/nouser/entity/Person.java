package com.nouser.entity;

public class Person {
    private String name;
    private Integer sex;
    private int age;
    private String address;
    private String no;

    public Person() {
    }

    public Person(String name, Integer sex, int age, String address, String no) {
        this.name = name;
        this.sex = sex;
        this.age = age;
        this.address = address;
        this.no = no;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    @Override
    public String toString() {
        return "Person{" +
                "customKey='" + name + '\'' +
                ", sex=" + sex +
                ", age=" + age +
                ", address='" + address + '\'' +
                ", no='" + no + '\'' +
                '}';
    }
}
