package com.stone.microstone.repository.workbook.pdf;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.WorkBookPDF;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkBookPdfRepository{
    WorkBookPDF findByWorkBook(WorkBook workBook);
    WorkBookPDF save(WorkBookPDF workBookPDF);
    WorkBookPDF getByWorkBookWithNonNullPdfPath(WorkBook workBook);
}
