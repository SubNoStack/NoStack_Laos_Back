package com.stone.microstone.repository.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.LocalUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

//유저 문제집정보를 조회하는 구현체 클래스
@Repository
public class WorkBookRepositoryImpl implements WorkBookRepository {

    @PersistenceContext
    private EntityManager em;

    public WorkBookRepositoryImpl(EntityManager em) {
        this.em=em;
    }



    //문제집 저장 수행 메소드
    @Override
    public WorkBook save(WorkBook workBook) {
        if(workBook.getWb_id()==0){
            em.persist(workBook);
        }else{
            em.merge(workBook);
        }
        return workBook;
    }


    //생성된 문제id와 유저 정보를 이용해 문제정보를 조회하는 메소드
    @Override
    public Optional<WorkBook> findByuserIdandUser(int id, LocalUser user) {
        List<WorkBook> result=em.createQuery("SELECT w FROM WorkBook w WHERE w.wb_user_id=:id AND w.user=:user",WorkBook.class)
                .setParameter("id",id)
                .setParameter("user",user)
                .getResultList();
        return result.stream().findFirst();
    }

    //생성된 id와 유저정보로 문제집 정보를 삭제하는 메소드.
    @Override
    public void deleteById(int id, LocalUser user) {
        em.createQuery("DELETE FROM WorkBook wb WHERE wb.wb_user_id=:id AND wb.user=:user")
                .setParameter("id",id)
                .setParameter("user",user)
                .executeUpdate();
    }


    //유저가 제일 마지막으로 생성했던 문제집 정보를 조회하는 메소드
    @Override
    public Optional<WorkBook> findLastWorkBook(LocalUser user) {
        return em.createQuery("SELECT w FROM WorkBook w WHERE w.user=:user ORDER BY w.wb_user_id DESC",WorkBook.class )
                .setParameter("user",user)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }


    //유저가 생성한 문제집을 전체 조회하는 메소드.
    @Override
    public List<WorkBook> findByUser(LocalUser user) {
        return em.createQuery("SELECT w FROM WorkBook w WHERE w.user = :user", WorkBook.class)
                .setParameter("user", user)
                .getResultList();
    }

    //유저가 즐겨찾기한 문제집을 조회하는 메소드
    @Override
    public List<WorkBook> findByUserfavoirte(LocalUser user) {
        return em.createQuery("SELECT w FROM WorkBook w WHERE w.user = :user AND w.wb_favorite=true", WorkBook.class)
                .setParameter("user", user)
                .getResultList();
    }


    //전체 문제집정보를 삭제하는 메소드
    public void deleteAll() {
        em.createQuery("DELETE FROM WorkBook").executeUpdate();
        em.createNativeQuery("ALTER TABLE WorkBook AUTO_INCREMENT = 1").executeUpdate();
    }

    //유저가 마지막으로 생성한 문제집의 생성 번호를 조회하는 메소드.
    @Override
    public Optional<Integer> findMaxUserid(LocalUser user) {
        Integer maxUserId = em.createQuery(
                "SELECT MAX(w.wb_user_id) FROM WorkBook w WHERE w.user = :user", Integer.class)
                .setParameter("user", user)
                .getSingleResult();
        return Optional.ofNullable(maxUserId);
    }

    //유저가 pdf를 생성하지 않은 것을 제외하고 전체 문제집 정보를 조회하는 메소드.
    @Override
    public List<WorkBook> findByUserAndpdf(LocalUser user){
        return em.createQuery("SELECT w FROM WorkBook w "+
                        "INNER JOIN w.workBookPDF p "+
                        "WHERE w.user = :user"+
                        " and p.pdf_path is not null"
                        , WorkBook.class)
                .setParameter("user", user)
                .getResultList();
    }

    //유저가 pdf를 생성하지 않은 것을 제외하고 전체 답지 정보를 조회하는 메소드.
    @Override
    public List<WorkBook> findByUserAndAnsPdf(LocalUser user){
        return em.createQuery("SELECT w FROM WorkBook w "+
                        "INNER JOIN w.answerPDF p "+
                        "WHERE w.user = :user"+
                        " and p.pdf_path is not null"
                        , WorkBook.class)
                .setParameter("user", user)
                .getResultList();
    }


}
