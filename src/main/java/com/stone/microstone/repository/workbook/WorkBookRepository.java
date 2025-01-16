package com.stone.microstone.repository.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;

import java.util.List;
import java.util.Optional;

public interface WorkBookRepository {

    WorkBook save(WorkBook workBook);

    Optional<WorkBook> findByuserId(int id);
    void deleteById(int id);

    Optional<WorkBook> findLastWorkBook();


    void deleteAll();

    Optional<Integer> findMaxUserid();

    List<WorkBook> findByUserAndpdf();

    List<WorkBook> findByUserAndAnsPdf();

}
