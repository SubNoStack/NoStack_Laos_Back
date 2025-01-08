package com.stone.microstone.repository.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;

import java.util.List;
import java.util.Optional;

public interface WorkBookRepository {

    WorkBook save(WorkBook workBook);

    Optional<WorkBook> findByuserIdandUser(int id, LocalUser user);
    void deleteById(int id, LocalUser user);

    Optional<WorkBook> findLastWorkBook(LocalUser user);

    List<WorkBook> findByUser(LocalUser user);

    List<WorkBook> findByUserfavoirte(LocalUser user);

    void deleteAll();

    Optional<Integer> findMaxUserid(LocalUser user);

    List<WorkBook> findByUserAndpdf(LocalUser user);

    List<WorkBook> findByUserAndAnsPdf(LocalUser user);
}
