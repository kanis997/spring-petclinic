/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class VisitPersistenceTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void duplicateVisitIsNotPersisted() throws Exception {
		int ownerId = 1;
		int petId = 1;
		LocalDate date = LocalDate.now().plusDays(2);
		Owner owner = this.owners.findById(ownerId).orElseThrow();
		Pet pet = owner.getPet(petId);
		Visit existingVisit = new Visit();
		existingVisit.setDate(date);
		existingVisit.setDescription("Existing visit");
		pet.addVisit(existingVisit);
		this.owners.saveAndFlush(owner);
		this.entityManager.clear();

		int initialVisitCount = this.owners.findById(ownerId).orElseThrow().getPet(petId).getVisits().size();

		this.mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", ownerId, petId).param("date", date.toString())
				.param("description", "Duplicate visit"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"))
			.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"));

		new TransactionTemplate(this.transactionManager).executeWithoutResult(transactionStatus -> {
			this.entityManager.flush();
			this.entityManager.clear();

			Owner reloadedOwner = this.owners.findById(ownerId).orElseThrow();
			assertThat(reloadedOwner.getPet(petId).getVisits()).hasSize(initialVisitCount);
			assertThat(reloadedOwner.getPet(petId).getVisits()).filteredOn(visit -> date.equals(visit.getDate()))
				.hasSize(1)
				.allSatisfy(visit -> assertThat(visit.getDescription()).isEqualTo("Existing visit"));
		});
	}

}
