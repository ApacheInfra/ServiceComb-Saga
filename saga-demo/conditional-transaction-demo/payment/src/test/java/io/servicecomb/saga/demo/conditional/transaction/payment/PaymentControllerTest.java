/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.saga.demo.conditional.transaction.payment;

import static org.hamcrest.core.Is.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Test
  public void respondWithChildren_IfTotalPurchaseIsLowerThanThreshold() throws Exception {
    mockMvc.perform(post("/payment")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .content("customerId=mike"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sagaChildren[0]", is("inventory")));

    mockMvc.perform(post("/payment")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .content("customerId=mike"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sagaChildren[0]", is("inventory")));

    mockMvc.perform(post("/payment")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .content("customerId=mike"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sagaChildren[0]").doesNotExist());
  }
}
