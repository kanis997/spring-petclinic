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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Test class for {@link VisitController}
 *
 * @author Colin But
 * @author Wick Dynex
 */
@WebMvcTest(VisitController.class)
@DisabledInNativeImage
@DisabledInAotMode
class VisitControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	private Owner owner;

	@BeforeEach
	void init() {
		this.owner = new Owner();
		Pet pet = new Pet();
		this.owner.addPet(pet);
		pet.setId(TEST_PET_ID);
		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(this.owner));
	}

	@Test
	void initNewVisitForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

	@Test
	void processNewVisitFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("name", "George")
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("description", "Visit Description"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));

		verify(this.owners).save(this.owner);
	}

	@Test
	void processNewVisitFormRejectsDuplicateDateWithDifferentDescription() throws Exception {
		LocalDate date = LocalDate.now().plusDays(2);
		addExistingVisit(TEST_PET_ID, date, "Original description");

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", date.toString())
				.param("description", "Different description"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"))
			.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"))
			.andExpect(content().string(containsString("A visit is already booked for this pet on this date")));

		verify(this.owners, never()).save(any(Owner.class));
	}

	@Test
	void processNewVisitFormRejectsDuplicateDateWithSameDescription() throws Exception {
		LocalDate date = LocalDate.now().plusDays(2);
		addExistingVisit(TEST_PET_ID, date, "Visit description");

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", date.toString())
				.param("description", "Visit description"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"))
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.owners, never()).save(any(Owner.class));
	}

	@Test
	void processNewVisitFormAllowsAnotherDateForSamePet() throws Exception {
		LocalDate bookedDate = LocalDate.now().plusDays(2);
		addExistingVisit(TEST_PET_ID, bookedDate, "Original description");

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", bookedDate.plusDays(1).toString())
				.param("description", "Next visit"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));

		verify(this.owners).save(this.owner);
	}

	@Test
	void processNewVisitFormAllowsSameDateForDifferentPet() throws Exception {
		Pet anotherPet = new Pet();
		this.owner.addPet(anotherPet);
		anotherPet.setId(TEST_PET_ID + 1);
		LocalDate date = LocalDate.now().plusDays(2);
		addExistingVisit(anotherPet.getId(), date, "Other pet visit");

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", date.toString())
				.param("description", "This pet visit"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));

		verify(this.owners).save(this.owner);
	}

	@Test
	void processNewVisitFormKeepsInvalidDateBindingError() throws Exception {
		addExistingVisit(TEST_PET_ID, LocalDate.now().plusDays(1), "Original description");

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", "invalid-date")
				.param("description", "Visit description"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("visit", "date"))
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.owners, never()).save(any(Owner.class));
	}

	private void addExistingVisit(int petId, LocalDate date, String description) {
		Visit visit = new Visit();
		visit.setId(99);
		visit.setDate(date);
		visit.setDescription(description);
		this.owner.getPet(petId).addVisit(visit);
	}

	@Test
	void processNewVisitFormHasErrors() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID).param("name",
					"George"))
			.andExpect(model().attributeHasErrors("visit"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

	@Test
	void processNewVisitFormHasErrorsWhenVisitDateIsNotInFuture() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("name", "George")
				.param("date", LocalDate.now().toString())
				.param("description", "Visit Description"))
			.andExpect(model().attributeHasFieldErrors("visit", "date"))
			.andExpect(model().attributeHasFieldErrorCode("visit", "date", "typeMismatch.visitDate"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.owners, never()).save(any(Owner.class));
	}

	@Test
	void processNewVisitFormHasErrorsWhenDescriptionIsTooLong() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("name", "George")
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("description", "a".repeat(256)))
			.andExpect(model().attributeHasFieldErrors("visit", "description"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

}
