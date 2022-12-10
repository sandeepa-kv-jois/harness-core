// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"net/http"

	"github.com/harness/harness-core/queue-service/hsqs/store"
	"github.com/labstack/echo/v4"
)

type Handler struct {
	s store.Store
}

func NewHandler(s store.Store) *Handler {
	return &Handler{s: s}
}

func (h *Handler) Register(g *echo.Group) {
	g.POST("/queue", h.handleEnqueue())
	g.POST("/dequeue", h.handleDequeue())
	g.POST("/ack", h.ack())
	g.POST("/unack", h.unAck())
	g.GET("/healthz", h.healthz())
	g.POST("/register", h.register())
}

// handleEnqueue godoc
// @Summary     Enqueue
// @Description Enqueue the request
// @Accept      json
// @Produce     json
// @Param       request body store.EnqueueRequest true "query params"
// @Param 		Authorization header string true "Authorization"
// @Success     200 {object} store.EnqueueResponse
// @Router      /v1/queue [POST]
func (h *Handler) handleEnqueue() echo.HandlerFunc {
	return func(c echo.Context) error {

		// bind request body to enqueue request
		p := &store.EnqueueRequest{}

		if err := c.Bind(p); err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}

		enqueue, err := h.s.Enqueue(c.Request().Context(), *p)
		if err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}
		return c.JSON(http.StatusOK, enqueue)
	}
}

// handleDequeue godoc
// @Summary     Dequeue in Redis
// @Description Dequeue a request
// @Accept      json
// @Produce     json
// @Param       request body store.DequeueRequest true "query params"
// @Param 		Authorization header string true "Authorization"
// @Success     200 {object} store.DequeueResponse
// @Router      /v1/dequeue [POST]
func (h *Handler) handleDequeue() echo.HandlerFunc {
	return func(c echo.Context) error {

		p := &store.DequeueRequest{}

		if err := c.Bind(p); err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}

		dequeue, err := h.s.Dequeue(c.Request().Context(), *p)
		if err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}
		return c.JSON(http.StatusOK, dequeue)
	}
}

// ack godoc
// @Summary     Ack a Redis message
// @Description Ack a Redis message consumed successfully
// @Accept      json
// @Produce     json
// @Param       request body store.AckRequest true "query params"
// @Param 		Authorization header string true "Authorization"
// @Success     200 {object} store.AckResponse
// @Router      /v1/ack [POST]
func (h *Handler) ack() echo.HandlerFunc {
	return func(c echo.Context) error {

		p := &store.AckRequest{}

		if err := c.Bind(p); err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}

		ack, err := h.s.Ack(c.Request().Context(), *p)
		if err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}
		return c.JSON(http.StatusOK, ack)
	}
}

// unAck godoc
// @Summary     UnAck a Redis message or SubTopic
// @Description UnAck a Redis message or SubTopic to stop processing
// @Accept      json
// @Produce     json
// @Param       request body store.UnAckRequest true "query params"
// @Param 		Authorization header string true "Authorization"
// @Success     200 {object} store.UnAckResponse
// @Router      /v1/unack [POST]
func (h *Handler) unAck() echo.HandlerFunc {
	return func(c echo.Context) error {

		p := &store.UnAckRequest{}

		if err := c.Bind(p); err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}

		unAck, err := h.s.UnAck(c.Request().Context(), *p)
		if err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}
		return c.JSON(http.StatusOK, unAck)
	}
}

// healthz godoc
// @Summary     Health API for Queue Service
// @Description Health API for Queue Service
// @Accept      json
// @Produce     json
// @Success     200 {object} string
// @Router      /v1/healthz [get]
func (h *Handler) healthz() echo.HandlerFunc {
	return func(c echo.Context) error {
		return c.JSON(http.StatusOK, "Queue Service is Healthy")
	}
}

// register godoc
// @Summary     Register a Topic to a particular Queue
// @Description Register a Topic to a particular Queue
// @Accept      json
// @Produce     json
// @Param       request body store.RegisterTopicMetadata true "query params"
// @Param 		Authorization header string true "Authorization"
// @Success     200 {object} string
// @Router      /v1/register [POST]
func (h *Handler) register() echo.HandlerFunc {
	return func(c echo.Context) error {

		p := &store.RegisterTopicMetadata{}

		if err := c.Bind(p); err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}

		err := h.s.Register(c.Request().Context(), *p)
		if err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}
		return c.JSON(http.StatusOK, "Registration completed successfully")
	}
}
