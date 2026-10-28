package lib.persistence.repositories;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.springframework.beans.factory.annotation.Value;

import lib.persistence.entities.Page;

public class BaseRepository<E> {
	
	@PersistenceContext
    protected EntityManager entityManager;
	
	@Value("${page.size}")	
	protected int pageSize;
	
	private String instanceNameOfUsedEntity; // Example: customer
	
	public BaseRepository() {
		
		instanceNameOfUsedEntity = String.valueOf(getClassNameOfUsedEntity().charAt(0)).toLowerCase() + getClassNameOfUsedEntity().substring(1);
	}
	
	public void save(E entity) {
		
		entityManager.persist(entity);
	}
	
	public E findById(int id) {
		
		String query = "SELECT " + instanceNameOfUsedEntity + " " + 
					   "FROM   " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " +
					   "WHERE  " + instanceNameOfUsedEntity + ".id = :idParam";

		E entity = entityManager.createQuery(query, getClassOfUsedEntity())
									.setParameter("idParam", id)
									.getSingleResult();	
	
		return entity;
	}
	
	public Object[] findById(int id, LinkedHashSet<String> pathsOfFields) {
		
		String fields = exractNamesOfFields(pathsOfFields);
		
		String query = "SELECT " + fields + " " +
					   "FROM   " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " +
					   "WHERE  " + instanceNameOfUsedEntity + ".id = :idParam";
		
		Object[] rawRecord = entityManager.createQuery(query, Object[].class)
											.setParameter("idParam", id)
											.getSingleResult();
	
		return rawRecord;
	}

	public Page<Object[]> findByPage(int pageIndex, LinkedHashSet<String> pathsOfFields) {
		
		String fields = exractNamesOfFields(pathsOfFields);
		
		String dataQuery = "SELECT " + fields + " " +
				           "FROM   " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " +
				           "ORDER BY " + instanceNameOfUsedEntity + ".id ASC";
		
		// pageIndex is zero based => will be translated to the index of the first requested element later
		int firstElementIndex = pageIndex * pageSize;
		
		ArrayList<Object[]> rawData = (ArrayList<Object[]>) entityManager.createQuery(dataQuery, Object[].class)
																			.setFirstResult(firstElementIndex)
																			.setMaxResults(pageSize)
																			.getResultList();		
		
		String countQuery = "SELECT COUNT(*) " + 
							"FROM   " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity;
		
		long totalElements = entityManager.createQuery(countQuery, Long.class)
											.getSingleResult();
		
		int totalPages = (int) Math.ceil((totalElements * 1.0) / pageSize);		
		boolean isFirstPage = (pageIndex == 0);
		boolean isLastPage = ((pageIndex + 1) == totalPages);
		
		Page<Object[]> page = new Page<Object[]>();
		page.setData(rawData);
		page.setFirstPage(isFirstPage);
		page.setLastPage(isLastPage);
		
		return page;
	}
	
	public ArrayList<E> findAll() {
		
		String dataQuery = "SELECT " + instanceNameOfUsedEntity + " " +
				           "FROM   " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " +
				           "ORDER BY " + instanceNameOfUsedEntity + ".id ASC";
		
		ArrayList<E> entities = (ArrayList<E>) entityManager.createQuery(dataQuery, getClassOfUsedEntity())
																.getResultList();		
		
		return entities;
	}
	
	public ArrayList<Object[]> findAll(LinkedHashSet<String> pathsOfFields) {
		
		String fields = exractNamesOfFields(pathsOfFields);
		
		String dataQuery = "SELECT " + fields + " " +
				           "FROM   " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " +
				           "ORDER BY " + instanceNameOfUsedEntity + ".id ASC";
		
		ArrayList<Object[]> rawData = (ArrayList<Object[]>) entityManager.createQuery(dataQuery, Object[].class)
																			.getResultList();		
		
		return rawData;
	}
	
	// assume the order of fields in both pathsOfFields and entityUpdateModel, so it'll give a match later in the form of (fieldName and fieldValue)
	public <U> void updateById(int id, LinkedHashSet<String> pathsOfFields, U entityUpdateModel) {
		
		String columnsToBeModified = "";
		int cursor1 = 0;
		Iterator<String> iterator = pathsOfFields.iterator();
		while ( iterator.hasNext() ) {
			
			String pathOfField = iterator.next();
			String fieldName = pathOfField.substring(pathOfField.lastIndexOf(".") + 1);
			
			String columnToBeModified = pathOfField + " = :" + fieldName + "Param";
			
			if ( cursor1 < pathsOfFields.size() - 1  ) {
				columnToBeModified += ", ";
			}
			
			columnsToBeModified += columnToBeModified;
			cursor1++;
		}
		
		String updateStatement = "UPDATE " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " + 
								 "SET    " + columnsToBeModified + " " +
								 "WHERE  " + instanceNameOfUsedEntity + ".id = :idParam";

		Query query = entityManager.createQuery(updateStatement);
			
		try {
			
			Method[] functionsOfEntityUpdateModel = entityUpdateModel.getClass().getMethods();
			for (int cursor2 = 0; cursor2 < functionsOfEntityUpdateModel.length; cursor2++) {
				
				String functionName = functionsOfEntityUpdateModel[cursor2].getName();
				if (functionName.startsWith("get")) {
					
					String fieldName = functionName.substring(3);
					fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
					
					Object retrievedValue = functionsOfEntityUpdateModel[cursor2].invoke(entityUpdateModel);
					
					query.setParameter(fieldName + "Param", retrievedValue);
				}				
			}
		
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {}
		
		query.setParameter("idParam", id)
				.executeUpdate();
	}
	
	public void deleteById(int id) {
		
		String deleteStatement = "DELETE FROM " + getClassNameOfUsedEntity() + " " + instanceNameOfUsedEntity + " " +
				 				 "WHERE       " + instanceNameOfUsedEntity + ".id = :idParam";

		entityManager.createQuery(deleteStatement)
						.setParameter("idParam", id)
						.executeUpdate();
	}
	
	/* ******************************************************************************************************************************* */
	
	private Class<E> getClassOfUsedEntity() {
		
		return (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}
	
	private String getClassNameOfUsedEntity() {
		
		return getClassOfUsedEntity().getSimpleName();
	}
	
	private String exractNamesOfFields(LinkedHashSet<String> pathsOfFields) {
		
		String fields = "";
		int cursor = 0;
		Iterator<String> iterator = pathsOfFields.iterator();
		while ( iterator.hasNext() ) {
			
			fields += iterator.next();
			if ( cursor < pathsOfFields.size() - 1  ) {
				fields += ", ";
			}
			cursor++;
		}
		return fields;
	}

}
