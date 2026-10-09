package lib.persistence.repositories;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.springframework.beans.factory.annotation.Value;

import lib.persistence.entities.Page;
import lib.persistence.repositories.exceptions.DataNotFoundException;

public class BaseRepository<E> {
	
	@PersistenceContext
    protected EntityManager entityManager;
	
	@Value("${page.size}")	
	protected int pageSize;
	
	private Class<E> entityType; // Example: Class<Customer>
	
	private String entityName; // Example: Customer
	
	private String instanceName; // Example: customer
	
	protected BaseRepository(Class<E> entityType) {
		
		this.entityType = entityType;
		entityName = entityType.getSimpleName();
		instanceName = String.valueOf(entityName.charAt(0)).toLowerCase() + entityName.substring(1);
	}
	
	public void save(E entity) {
		
		entityManager.persist(entity);
	}
	
	public E findById(int id) {
		
		String query = "SELECT " + instanceName + " " + 
					   "FROM   " + entityName + " " + instanceName + " " +
					   "WHERE  " + instanceName + ".id = :idParam";

		E entity;
		try {
			
			entity = entityManager.createQuery(query, entityType)
									.setParameter("idParam", id)
									.getSingleResult();			
			
		} catch (NoResultException e) {
			throw new DataNotFoundException();
		}
	
		return entity;
	}
	
	public Object[] findById(int id, LinkedHashSet<String> pathsOfFields) {
		
		String fields = exractNamesOfFields(pathsOfFields);
		
		String query = "SELECT " + fields + " " +
					   "FROM   " + entityName + " " + instanceName + " " +
					   "WHERE  " + instanceName + ".id = :idParam";
		
		Object[] rawRecord;
		try {
			
			rawRecord = entityManager.createQuery(query, Object[].class)
										.setParameter("idParam", id)
										.getSingleResult();
			
		} catch (NoResultException e) {
			throw new DataNotFoundException();
		}
	
		return rawRecord;
	}

	public Page<Object[]> findByPage(int pageIndex, LinkedHashSet<String> pathsOfFields) {
		
		String fields = exractNamesOfFields(pathsOfFields);
		
		String dataQuery = "SELECT " + fields + " " +
				           "FROM   " + entityName + " " + instanceName + " " +
				           "ORDER BY " + instanceName + ".id ASC";
		
		// pageIndex is zero based => will be translated to the index of the first requested element later
		int firstElementIndex = pageIndex * pageSize;
		
		ArrayList<Object[]> rawData = (ArrayList<Object[]>) entityManager.createQuery(dataQuery, Object[].class)
																			.setFirstResult(firstElementIndex)
																			.setMaxResults(pageSize)
																			.getResultList();		
		if (rawData.isEmpty()) {
			throw new DataNotFoundException();
		}
		
		String countQuery = "SELECT COUNT(*) " + 
							"FROM   " + entityName + " " + instanceName;
		
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
		
		String dataQuery = "SELECT " + instanceName + " " +
				           "FROM   " + entityName + " " + instanceName + " " +
				           "ORDER BY " + instanceName + ".id ASC";
		
		ArrayList<E> entities = (ArrayList<E>) entityManager.createQuery(dataQuery, entityType)
																.getResultList();		
		
		if (entities.isEmpty()) {
			throw new DataNotFoundException();
		}

		return entities;
	}
	
	public ArrayList<Object[]> findAll(LinkedHashSet<String> pathsOfFields) {
		
		String fields = exractNamesOfFields(pathsOfFields);
		
		String dataQuery = "SELECT " + fields + " " +
				           "FROM   " + entityName + " " + instanceName + " " +
				           "ORDER BY " + instanceName + ".id ASC";
		
		ArrayList<Object[]> rawData = (ArrayList<Object[]>) entityManager.createQuery(dataQuery, Object[].class)
																			.getResultList();		
		
		if (rawData.isEmpty()) {
			throw new DataNotFoundException();
		}

		return rawData;
	}
	
	public <U> void updateById(int id, LinkedHashSet<String> pathsOfFields, U entityUpdateModel) {
		
		String columnsToBeModified = "";
		int cursor = 0;
		Iterator<String> iterator = pathsOfFields.iterator();
		while ( iterator.hasNext() ) {
			
			String pathOfField = iterator.next();
			String fieldName = pathOfField.substring(pathOfField.lastIndexOf(".") + 1);
			
			String columnToBeModified = pathOfField + " = :" + fieldName + "Param";
			
			if ( cursor < pathsOfFields.size() - 1  ) {
				columnToBeModified += ", ";
			}
			
			columnsToBeModified += columnToBeModified;
			cursor++;
		}
		
		String updateStatement = "UPDATE " + entityName + " " + instanceName + " " + 
								 "SET    " + columnsToBeModified + " " +
								 "WHERE  " + instanceName + ".id = :idParam";

		Query query = entityManager.createQuery(updateStatement);
		
		Class<?> classOfUpdateModel = entityUpdateModel.getClass();
		Field[] fieldsOfUpdateModel = classOfUpdateModel.getDeclaredFields();
		for (int index = 0; index < fieldsOfUpdateModel.length; index++) {
			
            try {
            	
            	Field field = fieldsOfUpdateModel[index];
                field.setAccessible(true); // grant access to private fields
                                
                query.setParameter(field.getName() + "Param", field.get(entityUpdateModel));

            } catch (IllegalAccessException e) { }
        }
		
		query.setParameter("idParam", id)
				.executeUpdate();
	}
	
	public void deleteById(int id) {
		
		String deleteStatement = "DELETE FROM " + entityName + " " + instanceName + " " +
				 				 "WHERE       " + instanceName + ".id = :idParam";

		entityManager.createQuery(deleteStatement)
						.setParameter("idParam", id)
						.executeUpdate();
	}
	
	/* ******************************************************************************************************************************* */
	
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
