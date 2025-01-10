package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "work_question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int pr_id;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String pr_content;

    @Column
    private String pr_image_path;

    @Column
    private int pr_wb_id;

    @Column
    private String pr_image_name;

    @ManyToOne
    @JoinColumn(name = "wb_id",unique = true)
    private WorkBook workBook;

}
