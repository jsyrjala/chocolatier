(ns chocolatier.entities.player
  (:require [chocolatier.utils.logging :refer [debug info warn error]]
            [chocolatier.engine.core :refer [mk-state]]
            [chocolatier.engine.components.animateable :refer [mk-animateable-state]]
            [chocolatier.engine.components.renderable :refer [mk-sprite-state
                                                              mk-text-sprite-state]]
            [chocolatier.engine.components.collidable :refer [mk-collidable-state]]
            [chocolatier.engine.components.moveable :refer [mk-moveable-state]]
            [chocolatier.engine.components.attack :refer [mk-attack-state]]
            [chocolatier.engine.components.damage :refer [mk-damage-state]]
            [chocolatier.engine.components.position :refer [mk-position-state]]))


(defn create-player!
  "Create a player by initializing some component state"
  [stage loader uid pos-x pos-y pos-z map-x map-y]
  (info "Creating player" pos-x pos-y pos-z map-x map-y)
  (let [text-state (mk-text-sprite-state stage "Player 1" {"font" "bold 12px Arial"
                                                           "stroke" "white"
                                                           "strokeThickness" 3})
        sprite-state (mk-sprite-state stage loader "/img/test_spritesheet.png")
        animation-state (mk-animateable-state :stand-down
                                              [:stand-up 832 1344 64 64 8 0 1]
                                              [:stand-up-right 832 1344 64 64 8 0 1]
                                              [:stand-up-left 832 1344 64 64 8 0 1]
                                              [:stand-down 832 1344 64 64 10 0 1]
                                              [:stand-down-right 832 1344 64 64 10 0 1]
                                              [:stand-down-left 832 1344 64 64 10 0 1]
                                              [:stand-left 832 1344 64 64 9 0 1]
                                              [:stand-right 832 1344 64 64 11 0 1]
                                              [:walk-up 832 1344 64 64 8 0 9]
                                              [:walk-up-right 832 1344 64 64 8 0 9]
                                              [:walk-up-left 832 1344 64 64 8 0 9]
                                              [:walk-down 832 1344 64 64 10 0 9]
                                              [:walk-down-right 832 1344 64 64 10 0 9]
                                              [:walk-down-left 832 1344 64 64 10 0 9]
                                              [:walk-left 832 1344 64 64 9 0 9]
                                              [:walk-right 832 1344 64 64 11 0 9]
                                              [:fireball-up 832 1344 64 64 4 0 2]
                                              [:fireball-up-right 832 1344 64 64 4 0 2]
                                              [:fireball-up-left 832 1344 64 64 4 0 2]
                                              [:fireball-down 832 1344 64 64 6 0 2]
                                              [:fireball-down-right 832 1344 64 64 6 0 2]
                                              [:fireball-down-left 832 1344 64 64 6 0 2]
                                              [:fireball-left 832 1344 64 64 5 0 2]
                                              [:fireball-right 832 1344 64 64 7 0 2]
                                              [:spear-up 832 1344 64 64 4 0 8]
                                              [:spear-up-right 832 1344 64 64 4 0 8]
                                              [:spear-up-left 832 1344 64 64 4 0 8]
                                              [:spear-down 832 1344 64 64 6 0 8]
                                              [:spear-down-right 832 1344 64 64 6 0 8]
                                              [:spear-down-left 832 1344 64 64 6 0 8]
                                              [:spear-left 832 1344 64 64 5 0 8]
                                              [:spear-right 832 1344 64 64 7 0 8])
        position-state (mk-position-state pos-x pos-y pos-x pos-y pos-z)
        move-state (mk-moveable-state 4 :down)
        collision-state (mk-collidable-state 64 64 nil)
        attacks [[:fireball {:damage 10
                             :cooldown 8
                             :type :fire
                             :width 30
                             :height 30
                             :speed 10
                             :ttl 100
                             :sprite-fn #(mk-sprite-state stage
                                                          loader
                                                          "/img/fireball.png"
                                                          [0 0 30 30]
                                                          0)}]
                 [:spear {:damage 10
                          :cooldown 4
                          :type :fire
                          :width 10
                          :height 10
                          :speed 8
                          :ttl 2
                          :sprite-fn #(mk-sprite-state stage loader "/img/fireball.png")}]]
        attack-state (mk-attack-state attacks)
        damage-state (mk-damage-state 100 10 10 #(mk-text-sprite-state stage % {}))]
    {:uid uid
     :components [{:uid :position :state position-state}
                  {:uid :text :state {:text (name uid) :rotation 0}}
                  {:uid :text-sprite :state text-state}
                  {:uid :animateable :state animation-state}
                  {:uid :sprite :state sprite-state}
                  {:uid :keyboard-input}
                  {:uid :controllable}
                  {:uid :collidable :state collision-state}
                  {:uid :collision-debuggable}
                  {:uid :moveable :state move-state}
                  {:uid :attack :state attack-state}
                  {:uid :damage :state damage-state}]}))
